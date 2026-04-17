package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.*;
import com.example.common.exception.BusinessException;
import com.example.common.result.Result;
import com.example.order.dto.OrderDTO;
import com.example.order.entity.Order;
import com.example.order.feign.AccountFeignClient;
import com.example.order.feign.PaymentFeignClient;
import com.example.order.feign.StorageFeignClient;
import com.example.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private StorageFeignClient storageFeignClient;

    @Autowired
    private AccountFeignClient accountFeignClient;

    @Autowired
    private StorageCacheService storageCacheService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private TransactionMessageService transactionMessageService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(OrderCreateRequest request) {
        log.info("开始创建订单, 用户ID: {}, 商品ID: {}, 数量: {}", request.getUserId(), request.getProductId(), request.getQuantity());
        
        boolean cacheDeductSuccess = storageCacheService.deductStockWithRedis(request.getProductId(), request.getQuantity());
        if (!cacheDeductSuccess) {
            throw new BusinessException("库存不足,请选择其他商品下单");
        }
        
        String orderNo = generateOrderNo();

        StorageDeductRequest storageRequest = new StorageDeductRequest();
        storageRequest.setProductId(request.getProductId());
        storageRequest.setQuantity(request.getQuantity());
        storageRequest.setOrderNo(orderNo);
        Result<?> storageResult = storageFeignClient.deduct(storageRequest);
        if (!storageResult.isSuccess()) {
            storageCacheService.restoreStockWithRedis(request.getProductId(), request.getQuantity(), orderNo);
            throw new BusinessException(storageResult.getMessage());
        }

        try {
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(request.getUserId());
            order.setProductId(request.getProductId());
            order.setQuantity(request.getQuantity());
            order.setAmount(request.getAmount());
            order.setStatus(OrderStatus.PENDING.getValue());
            order.setCreatedTime(LocalDateTime.now());
            order.setUpdatedTime(order.getCreatedTime());
            orderMapper.insert(order);
            
            sendOrderTimeoutMessage(order);
            
            log.info("订单创建成功, 订单号: {}", orderNo);
            return convertToDTO(order);
        } catch (Exception e) {
            log.error("订单创建失败, 订单号: {}", orderNo, e);
            storageCacheService.restoreStockWithRedis(request.getProductId(), request.getQuantity(), orderNo);
            rollbackStorageDeduct(orderNo, request.getProductId(), request.getQuantity());
            throw new RuntimeException("创建订单失败,订单号:" + orderNo);
        }
    }


    private void sendPointsMessage(Order order) {
        try {
            PointsMessage pointsMessage = new PointsMessage();
            pointsMessage.setUserId(order.getUserId());
            pointsMessage.setOrderNo(order.getOrderNo());
            pointsMessage.setPoints(order.getAmount().intValue());

            rocketMQTemplate.sendMessageInTransaction(
                "points-tx-topic",
                MessageBuilder.withPayload(pointsMessage)
                    .setHeader("order_no", order.getOrderNo())
                    .build(),
                order.getOrderNo()
            );
            
            log.info("积分事务消息已发送，订单号: {}", order.getOrderNo());
        } catch (Exception e) {
            log.error("发送积分事务消息失败，降级保存到数据库，订单号: {}", order.getOrderNo(), e);
            saveFailedMessageToDb(order.getOrderNo(), "points-tx-topic", "points", 
                new PointsMessage(order.getUserId(), order.getOrderNo(), order.getAmount().intValue()));
        }
    }

    private void sendOrderTimeoutMessage(Order order) {
        OrderTimeoutMessage message = new OrderTimeoutMessage();
        message.setOrderNo(order.getOrderNo());
        message.setProductId(order.getProductId());
        message.setQuantity(order.getQuantity());
        message.setUserId(order.getUserId());
        message.setAmount(order.getAmount());
        try {
            rocketMQTemplate.syncSend(
                "order-timeout-topic",
                MessageBuilder.withPayload(message).build(),
                3000, 16
            );
            
            log.info("订单超时延迟消息已发送，订单号: {}", order.getOrderNo());
        } catch (Exception e) {
            log.error("发送订单超时消息失败，订单号: {}", order.getOrderNo(), e);
            saveFailedMessageToDb(order.getOrderNo(), "order-timeout-topic", "timeout", message);
            throw new BusinessException(500, "发送超时消息失败, 订单创建回滚:" + e);
        }
    }

    private void saveFailedMessageToDb(String orderNo, String topic, String tag, Object body) {
        try {
            com.example.order.entity.TransactionMessage message = new com.example.order.entity.TransactionMessage();
            message.setMessageId(java.util.UUID.randomUUID().toString().replace("-", ""));
            message.setTopic(topic);
            message.setTag(tag);
            message.setMessageBody(objectMapper.writeValueAsString(body));
            message.setStatus(com.example.order.constant.MessageStatus.PENDING.getValue());
            message.setRetryCount(0);
            message.setMaxRetryCount(3);
            message.setNextRetryTime(LocalDateTime.now());
            message.setErrorMessage("MQ发送失败，降级保存");
            transactionMessageService.saveMessage(message);
            log.warn("消息已保存到数据库待补偿，订单号: {}, topic: {}", orderNo, topic);
        } catch (Exception ex) {
            log.error("保存失败消息到数据库也失败，需人工介入，订单号: {}", orderNo, ex);
        }
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setProductId(order.getProductId());
        dto.setQuantity(order.getQuantity());
        dto.setAmount(order.getAmount());
        dto.setStatus(order.getStatus());
        return dto;
    }

    /**
     * 根据订单号获取订的状态
     */
    public Object queryOrderStatus(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    /**
     * 调用支付订单,控制重复提交的逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<?> payOrder(String orderNo) {
        String lockKey = "lock:pay:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                return Result.error("订单正在处理中,请勿重复提交");
            }
            log.info("获取锁成功,看门狗已启动,订单号: {}", orderNo);
            return doPayOrder(orderNo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("支付请求被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放锁成功，订单号: {}", orderNo);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<?> doPayOrder(String orderNo) {
        log.info("开始调用支付服务,订单号: {}", orderNo);
        
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));

        if (order == null) {
            log.error("订单不存在, 当前订单号: {}", orderNo);
            throw new BusinessException("提交的订单不存在");
        }
        
        if (order.getStatus() == OrderStatus.PAID.getValue()) {
            log.info("订单已支付, 请勿重复提交,订单号: {}", orderNo);
            return Result.success("订单已支付");
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED.getValue()) {
            log.warn("订单已取消, 订单号: {}", orderNo);
            throw new BusinessException("订单已取消，无法支付");
        }
        
        if (order.getStatus() == OrderStatus.TIMEOUT.getValue()) {
            log.warn("订单已超时, 订单号: {}", orderNo);
            throw new BusinessException("订单已超时，无法支付");
        }
        
        if (order.getStatus() != OrderStatus.PENDING.getValue()) {
            log.error("订单状态异常, 订单号: {}, 状态: {}", orderNo, order.getStatus());
            throw new BusinessException("订单状态异常，无法支付");
        }

        UpdateWrapper<Order> update = new UpdateWrapper<>();
        update.lambda().eq(Order::getOrderNo, orderNo).eq(Order::getStatus, OrderStatus.PENDING.getValue()).set(Order::getStatus, OrderStatus.PAID.getValue());

        int updated = orderMapper.update(null, update);
        if (updated == 0) {
            log.warn("订单状态更新失败，可能已被超时处理，订单号: {}", orderNo);
            throw new BusinessException("订单状态已变更，支付失败");
        }

        try {
            //扣款模拟支付成功的逻辑
            AccountDeductRequest request = new AccountDeductRequest();
            request.setOrderNo(orderNo);
            request.setUserId(order.getUserId());
            request.setAmount(order.getAmount());
            Result<?> deduct = accountFeignClient.deduct(request);
            if (!deduct.isSuccess()) {
                log.error("扣除用户余额失败, 订单号: {}, 原因: {}", orderNo, deduct.getMessage());
                rollbackOrderStatus(orderNo);
                throw new BusinessException("扣款失败: " + deduct.getMessage());
            }

            PaymentRequest paymentRequest = new PaymentRequest();
            paymentRequest.setOrderNo(orderNo);
            paymentRequest.setTransactionId("TXN" + System.currentTimeMillis() + orderNo);
            paymentRequest.setPayAmount(order.getAmount());
            paymentRequest.setPayChannel(1);
            Result<?> result = paymentFeignClient.insertPayment(paymentRequest);
            if (!result.isSuccess()) {
                log.error("添加支付记录失败, 订单号: {}, 原因: {}", orderNo, result.getMessage());
                rollbackAccountDeduct(orderNo, order.getUserId(), order.getAmount());
                rollbackOrderStatus(orderNo);
                throw new BusinessException("支付记录创建失败: " + result.getMessage());
            }

            log.info("支付成功, 订单号: {}", orderNo);
            sendPointsMessage(order);
            return Result.success("支付成功");
            
        } catch (Exception e) {
            log.error("支付过程异常，回滚订单状态，订单号: {}", orderNo, e);
            rollbackOrderStatus(orderNo);
            throw new BusinessException("支付失败: " + e.getMessage());
        }
    }


    /**
     *  库存回滚失败操作
     */
    private void rollbackStorageDeduct(String orderNo,Long productId,Integer quantity) {
        try {
            StorageRestoreRequest restoreRequest = new StorageRestoreRequest();
            restoreRequest.setOrderNo(orderNo);
            restoreRequest.setProductId(productId);
            restoreRequest.setQuantity(quantity);

            Result<?> restoreResult = storageFeignClient.restore(restoreRequest);
            if (restoreResult.isSuccess()) {
                log.info("订单:{} 的库存回滚成功, 订单号: {}", orderNo, orderNo);
            } else {
                log.error("订单号:{}库存回滚失败, 原因: {}", orderNo, restoreResult.getMessage());
            }
        } catch (Exception e) {
            log.error("库存回滚失败,需要人工介入, 订单号: {}", orderNo, e);
        }
    }
    /**
     * 订单支付失败，回滚订单状态
     */
    private void rollbackOrderStatus(String orderNo) {
        try {
            UpdateWrapper<Order> rollbackUpdate = new UpdateWrapper<>();
            rollbackUpdate.lambda().eq(Order::getOrderNo, orderNo).eq(Order::getStatus, OrderStatus.PAID.getValue())
                .set(Order::getStatus, OrderStatus.PENDING.getValue());
            
            int rolledBack = orderMapper.update(null, rollbackUpdate);
            if (rolledBack > 0) {
                log.info("订单状态回滚成功，订单号: {}", orderNo);
            } else {
                log.warn("订单状态回滚失败(可能已被超时处理)，订单号: {}", orderNo);
            }
        } catch (Exception e) {
            log.error("订单状态回滚异常，订单号: {}", orderNo, e);
        }
    }

    /**
     *  恢复账户金额
     */
    private void rollbackAccountDeduct(String orderNo, Long userId, BigDecimal amount) {
        try {
            AccountRestoreRequest restoreRequest = new AccountRestoreRequest();
            restoreRequest.setOrderNo(orderNo);
            restoreRequest.setUserId(userId);
            restoreRequest.setAmount(amount);
            
            Result<?> restoreResult = accountFeignClient.restore(restoreRequest);
            if (restoreResult.isSuccess()) {
                log.info("账户扣款回滚成功，订单号: {}", orderNo);
            } else {
                log.error("账户扣款回滚失败，需要人工介入，订单号: {}, 原因: {}", orderNo, restoreResult.getMessage());
            }
        } catch (Exception e) {
            log.error("账户扣款回滚异常，需要人工介入, 订单号: {}", orderNo, e);
        }
    }
}