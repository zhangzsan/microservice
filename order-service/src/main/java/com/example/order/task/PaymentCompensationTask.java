package com.example.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.dto.PointsMessage;
import com.example.common.result.Result;
import com.example.order.constant.CompensationResult;
import com.example.order.constant.MessageStatus;
import com.example.order.entity.Order;
import com.example.order.entity.PaymentRecord;
import com.example.order.entity.TransactionMessage;
import com.example.order.feign.AccountFeignClient;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.PaymentRecordMapper;
import com.example.order.service.TransactionMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class PaymentCompensationTask {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private AccountFeignClient accountFeignClient;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private TransactionMessageService transactionMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 300000) //每5分钟执行一次
    public void compensateStuckPayments() {
        log.info("开始执行支付异常订单补偿任务");
        //查找创建时间在35分钟内的订单
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(35);
        
        List<Order> stuckOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                .lt(Order::getCreatedTime, timeoutThreshold)
                .last("LIMIT 50"));

        if (stuckOrders.isEmpty()) {
            log.debug("无卡单订单");
            return;
        }

        log.warn("发现 {} 个卡单订单，开始自动补偿", stuckOrders.size());
        
        int successCount = 0;
        int failCount = 0;
        int manualCount = 0;
        
        for (Order order : stuckOrders) {
            try {
                CompensationResult result = handleStuckOrder(order);
                switch (result) {
                    case COMPENSATED:
                    case NO_ACTION_NEEDED:
                        successCount++;
                        break;
                    case MANUAL_INTERVENTION:
                        manualCount++;
                        break;
                    case FAILED:
                        failCount++;
                        break;
                }
            } catch (Exception e) {
                log.error("补偿订单异常, 订单号: {}", order.getOrderNo(), e);
                failCount++;
            }
        }
        
        log.info("支付补偿任务完成 - 成功: {}, 需人工介入: {}, 失败: {}", successCount, manualCount, failCount);
    }

    private CompensationResult handleStuckOrder(Order order) {
        String orderNo = order.getOrderNo();
        log.info("处理卡单订单, 订单号: {}, 用户ID: {}, 创建时间: {}", orderNo, order.getUserId(), order.getCreatedTime());

        PaymentRecord paymentRecord = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderNo, orderNo));

        if (paymentRecord != null) {
            log.warn("订单存在支付记录但未更新状态, 尝试补偿，订单号: {}", orderNo);
            return compensatePaidOrder(order, paymentRecord);
        } else {
            log.info("订单无支付记录, 检查是否需要取消，订单号: {}", orderNo);
            return handleUnpaidOrder(order);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    private CompensationResult compensatePaidOrder(Order order, PaymentRecord paymentRecord) {
        String orderNo = order.getOrderNo();
        
        try {
            // CAS更新订单状态: PENDING -> PAID
            LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                .set(Order::getStatus, OrderStatus.PAID.getValue());

            int updated = orderMapper.update(null, updateWrapper);
            if (updated > 0) {
                log.info("订单状态补偿成功，订单号: {}, 从PENDING更新为PAID", orderNo);
                sendPointsMessage(order);
                return CompensationResult.COMPENSATED;
            } else {
                // 更新失败,查询当前状态判断原因
                Order currentOrder = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
                if (currentOrder == null) {
                    log.error("订单不存在，需要人工介入, 订单号: {}", orderNo);
                    return CompensationResult.MANUAL_INTERVENTION;
                }
                
                if (currentOrder.getStatus() == OrderStatus.PAID.getValue()) {
                    log.info("订单已被其他线程补偿为PAID,订单号: {}", orderNo);
                    return CompensationResult.NO_ACTION_NEEDED;
                } else if (currentOrder.getStatus() == OrderStatus.TIMEOUT.getValue()) {
                    log.warn("订单已超时, 需要回滚支付, 订单号: {}", orderNo);
                    return rollbackPaymentAndNotify(order, paymentRecord);
                } else {
                    log.error("订单状态异常，需要人工介入, 订单号: {}, 当前状态: {}", orderNo, currentOrder.getStatus());
                    return CompensationResult.MANUAL_INTERVENTION;
                }
            }
        } catch (Exception e) {
            log.error("补偿支付订单失败，订单号: {}", orderNo, e);
            return CompensationResult.FAILED;
        }
    }

    /**
     * 回滚支付并通知账户服务
     * 注意:此方法有@Transactional,但包含远程调用,需确保账户服务的restore接口幂等
     */
    private CompensationResult rollbackPaymentAndNotify(Order order, PaymentRecord paymentRecord) {
        String orderNo = order.getOrderNo();
        
        try {
            log.warn("订单已超时但存在支付记录, 需要回滚，订单号: {}", orderNo);
            
            // 调用账户服务回滚扣款(远程调用,不受本地事务控制)
            AccountRestoreRequest restoreRequest = new AccountRestoreRequest();
            restoreRequest.setOrderNo(orderNo);
            restoreRequest.setUserId(order.getUserId());
            restoreRequest.setAmount(order.getAmount());
            
            Result<?> restoreResult = accountFeignClient.restore(restoreRequest);
            
            if (!restoreResult.isSuccess()) {
                log.error("账户扣款回滚失败, 需要人工介入, 订单号: {}, 原因: {}", orderNo, restoreResult.getMessage());
                return CompensationResult.MANUAL_INTERVENTION;
            }
            
            log.info("账户扣款回滚成功, 订单号: {}", orderNo);
            
            // 更新支付记录状态为已退款(本地事务)
            LambdaUpdateWrapper<PaymentRecord> updateWrapper = new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, paymentRecord.getId())
                .set(PaymentRecord::getPayStatus, 2); // TODO: 使用常量替代魔法数字
            
            paymentRecordMapper.update(null, updateWrapper);
            log.info("支付记录状态已标记为已退款，订单号: {}", orderNo);
            
            return CompensationResult.COMPENSATED;
            
        } catch (Exception e) {
            log.error("回滚支付异常，需要人工介入，订单号: {}", orderNo, e);
            return CompensationResult.MANUAL_INTERVENTION;
        }
    }

    private CompensationResult handleUnpaidOrder(Order order) {
        String orderNo = order.getOrderNo();
        
        // 直接使用传入的order对象,避免重复查询
        Integer status = order.getStatus();
        
        if (status == OrderStatus.TIMEOUT.getValue() || status == OrderStatus.CANCELLED.getValue()) {
            log.info("订单已被超时处理, 无需操作, 订单号: {}", orderNo);
            return CompensationResult.NO_ACTION_NEEDED;
        }
        
        if (status != OrderStatus.PENDING.getValue()) {
            log.warn("订单状态异常, 需要人工介入, 订单号: {}, 当前状态: {}", orderNo, status);
            return CompensationResult.MANUAL_INTERVENTION;
        }
        
        log.info("订单未支付且仍为PENDING状态, 等待超时消息处理, 订单号: {}", orderNo);
        return CompensationResult.NO_ACTION_NEEDED;
    }

    private void sendPointsMessage(Order order) {
        try {
            PointsMessage pointsMessage = buildPointsMessage(order);

            rocketMQTemplate.sendMessageInTransaction(
                "points-tx-topic", 
                MessageBuilder.withPayload(pointsMessage)
                    .setHeader("order_no", order.getOrderNo())
                    .build(),
                order.getOrderNo()
            );
            
            log.info("补偿发送积分事务消息成功, 订单号: {}", order.getOrderNo());
        } catch (Exception e) {
            log.error("发送积分事务消息失败，降级保存到数据库, 订单号: {}", order.getOrderNo(), e);
            saveFailedMessageToDb(order.getOrderNo(), "points-tx-topic", "points", 
                buildPointsMessage(order));
        }
    }

    /**
     * 构建积分消息对象
     */
    private PointsMessage buildPointsMessage(Order order) {
        PointsMessage message = new PointsMessage();
        message.setUserId(order.getUserId());
        message.setOrderNo(order.getOrderNo());
        message.setPoints(order.getAmount().intValue());
        return message;
    }

    private void saveFailedMessageToDb(String orderNo, String topic, String tag, Object body) {
        try {
            TransactionMessage message = new TransactionMessage();
            message.setMessageId(java.util.UUID.randomUUID().toString().replace("-", ""));
            message.setTopic(topic);
            message.setTag(tag);
            message.setMessageBody(objectMapper.writeValueAsString(body));
            message.setStatus(MessageStatus.PENDING.getValue());
            message.setRetryCount(0);
            message.setMaxRetryCount(3);
            message.setNextRetryTime(LocalDateTime.now());
            message.setErrorMessage("MQ发送失败,降级保存");
            transactionMessageService.saveMessage(message);
            log.warn("消息已保存到数据库待补偿，订单号: {}, topic: {}", orderNo, topic);
        } catch (Exception ex) {
            log.error("保存失败消息到数据库也失败, 需人工介入，订单号: {}", orderNo, ex);
        }
    }
}
