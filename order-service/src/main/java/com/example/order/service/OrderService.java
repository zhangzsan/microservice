package com.example.order.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.*;
import com.example.common.exception.BusinessException;
import com.example.common.result.Result;
import com.example.order.constant.MessageStatus;
import com.example.order.constant.RedisConstant;
import com.example.order.dto.OrderDTO;
import com.example.order.entity.Order;
import com.example.order.entity.TransactionMessage;
import com.example.order.feign.AccountFeignClient;
import com.example.order.feign.StorageFeignClient;
import com.example.order.mapper.OrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private PaymentService paymentService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private TransactionMessageService transactionMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @SentinelResource(value = "create-order", blockHandler = "handleCreateOrderBlock")
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(OrderCreateRequest request) {
        log.info("开始创建订单, 用户ID: {}, 商品ID: {}, 数量: {}", request.getUserId(), request.getProductId(), request.getQuantity());
        
        String orderNo = request.getOrderNo();
        if(StringUtils.isEmpty(orderNo)){
            throw new BusinessException("请求参数不合法");
        }

        /*
         * 控制10分钟内不能重复提交
         */
        String processingKey = RedisConstant.ORDER_PROCESSING + orderNo;
        Boolean isFirstSubmit = stringRedisTemplate.opsForValue().setIfAbsent(processingKey, "processing", 10, TimeUnit.MINUTES);
        
        if (Boolean.FALSE.equals(isFirstSubmit)) {
            log.warn("订单号正在处理中或已处理,拒绝重复提交, 订单号: {}", orderNo);
            throw new BusinessException("订单正在处理中,请勿重复提交");
        }
        
        try {
            return doCreateOrder(request, orderNo);
        } catch (Exception e) {
            stringRedisTemplate.delete(processingKey);
            log.error("订单创建失败,已清除处理标记, 订单号: {}", orderNo, e);
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 执行订单创建的核心逻辑
     */
    private OrderDTO doCreateOrder(OrderCreateRequest request, String orderNo) {
        boolean cacheDeductSuccess = storageCacheService.deductStockWithRedis(request.getProductId(), request.getQuantity());
        if (!cacheDeductSuccess) {
            throw new BusinessException("库存不足,请选择其他商品下单");
        }

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
            
            // 如果订单号已存在,会抛出DuplicateKeyException
            orderMapper.insert(order);
            
            // 订单创建成功后,再发送超时消息
            sendOrderTimeoutMessage(order);
            log.info("订单创建成功, 订单号: {}", orderNo);
            return convertToDTO(order);
        } catch (DuplicateKeyException e) {
            log.warn("订单号已存在,防止重复提交, 订单号: {}", orderNo);
            storageCacheService.restoreStockWithRedis(request.getProductId(), request.getQuantity(), orderNo);
            rollbackStorageDeduct(orderNo, request.getProductId(), request.getQuantity());
            throw new BusinessException("订单已存在,请勿重复提交");
        } catch (Exception e) {
            log.error("订单创建失败, 订单号: {}", orderNo, e);
            storageCacheService.restoreStockWithRedis(request.getProductId(), request.getQuantity(), orderNo);
            rollbackStorageDeduct(orderNo, request.getProductId(), request.getQuantity());
            throw new BusinessException("创建订单失败,订单号:" + orderNo);
        }
    }

    public Result<?> handleCreateOrderBlock(OrderCreateRequest request, BlockException ex) {
        log.warn("创建订单触发限流, 用户ID: {}", request.getUserId());
        return Result.error("系统繁忙, 请稍后重试");
    }

    private void sendPointsMessage(Order order) {
        try {
            PointsMessage pointsMessage = new PointsMessage();
            pointsMessage.setUserId(order.getUserId());
            pointsMessage.setOrderNo(order.getOrderNo());
            pointsMessage.setPoints(order.getAmount().intValue());
            rocketMQTemplate.sendMessageInTransaction("points-tx-topic", MessageBuilder.withPayload(pointsMessage).setHeader("order_no", order.getOrderNo()).build(), order.getOrderNo());
            log.info("积分事务消息已发送，订单号: {}", order.getOrderNo());
        } catch (Exception e) {
            log.error("发送积分事务消息失败，降级保存到数据库, 订单号: {}", order.getOrderNo(), e);
            saveFailedMessageToDb(order.getOrderNo(), "points-tx-topic", "points", new PointsMessage(order.getUserId(), order.getOrderNo(), order.getAmount().intValue()));
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
            rocketMQTemplate.syncSend("order-timeout-topic", MessageBuilder.withPayload(message).build(), 3000, 16);
            log.info("订单超时延迟消息已发送，订单号: {}", order.getOrderNo());
        } catch (Exception e) {
            log.error("发送订单超时消息失败，降级保存到数据库, 订单号: {}", order.getOrderNo(), e);
            // 降级保存,不影响订单创建
            saveFailedMessageToDb(order.getOrderNo(), "order-timeout-topic", "timeout", message);
        }
    }
    private void saveFailedMessageToDb(String orderNo, String topic, String tag, Object body) {
        try {
            TransactionMessage message = new TransactionMessage();
            message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
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


    private String generateOrderNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "order:no:gen:" + dateStr;
        // 利用Redis原子自增生成序列号,设置过期时间为2天防止键堆积
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1) {
            stringRedisTemplate.expire(key, 2, TimeUnit.DAYS);
        }
        // 格式：ORD2026041800000001
        return String.format("ORD%s%08d", dateStr, seq);
    }

    /**
     * 预生成并预留订单号(用于防重复提交)
     * 前端进入下单页时调用,获取订单号后在提交时携带
     * 订单号会被标记为“已预留”,5分钟后未使用则自动释放
     */
    public String generateAndReserveOrderNo() {
        String orderNo = generateOrderNo();
        // 在Redis中标记该订单号已被预留,有效期5分钟
        String reserveKey = "order:reserve:" + orderNo;
        stringRedisTemplate.opsForValue().set(reserveKey, "reserved", 5, TimeUnit.MINUTES);
        log.info("订单号已预留: {}, 5分钟内有效", orderNo);
        return orderNo;
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
    @SentinelResource(value = "pay-order", blockHandler = "handlePayOrderBlock")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> payOrder(String orderNo) {
        String lockKey = RedisConstant.LOCK_PAY + orderNo;
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

    public Result<?> handlePayOrderBlock(String orderNo, BlockException ex) {
        log.warn("支付订单触发限流，订单号: {}", orderNo);
        return Result.error("系统繁忙, 请稍后重试");
    }

    public Result<?> doPayOrder(String orderNo) {
        log.info("开始调用支付服务,订单号: {}", orderNo);
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            log.error("订单不存在, 当前订单号: {}", orderNo);
            throw new BusinessException("提交的订单不存在,请检查提交的订单号是否正确");
        }

        if (order.getStatus() == OrderStatus.PAID.getValue()) {
            log.info("订单已支付, 请勿重复提交,订单号: {}", orderNo);
            return Result.success("订单已支付,请勿重复支付");
        }

        if (order.getStatus() == OrderStatus.CANCELLED.getValue()) {
            log.warn("订单已取消, 订单号: {}", orderNo);
            throw new BusinessException("订单已取消, 无法支付");
        }

        if (order.getStatus() == OrderStatus.TIMEOUT.getValue()) {
            log.warn("订单已超时, 订单号: {}", orderNo);
            throw new BusinessException("订单已超时, 无法支付");
        }

        if (order.getStatus() != OrderStatus.PENDING.getValue()) {
            log.error("订单状态异常, 订单号: {}, 状态: {}", orderNo, order.getStatus());
            throw new BusinessException("订单状态异常, 无法支付");
        }

        UpdateWrapper<Order> update = new UpdateWrapper<>();
        update.lambda().eq(Order::getOrderNo, orderNo).eq(Order::getStatus, OrderStatus.PENDING.getValue()).set(Order::getStatus, OrderStatus.PAID.getValue());

        int updated = orderMapper.update(null, update);
        if (updated == 0) {
            log.warn("订单状态更新失败，可能已被超时处理, 订单号: {}, 请检查当前订单状态", orderNo);
            throw new BusinessException("订单状态已变更, 支付失败");
        }

        boolean deductSuccess = false;
        try {
            //模拟扣款操作
            AccountDeductRequest request = new AccountDeductRequest();
            request.setOrderNo(orderNo);
            request.setUserId(order.getUserId());
            request.setAmount(order.getAmount());
            Result<?> deduct = accountFeignClient.deduct(request);
            deductSuccess = deduct.isSuccess();
            if (!deductSuccess) {
                log.error("扣除用户余额失败, 订单号: {}, 原因: {}", orderNo, deduct.getMessage());
                throw new BusinessException("扣款失败: " + deduct.getMessage());
            }

            PaymentRequest paymentRequest = new PaymentRequest();
            paymentRequest.setOrderNo(orderNo);
            paymentRequest.setTransactionId("TXN" + System.currentTimeMillis() + orderNo);
            paymentRequest.setPayAmount(order.getAmount());
            paymentRequest.setPayChannel(1);
            
            // 检查支付记录是否真正插入成功
            boolean paymentInserted = paymentService.insertPayment(paymentRequest);
            if (!paymentInserted) {
                log.warn("支付记录已存在,可能是重复支付,订单号: {}", orderNo);
                // 幂等情况:记录已存在,认为是支付成功,但不发送积分消息(由首次支付发送)
                return Result.success("订单已支付,请勿重复支付");
            }
            
            log.info("支付成功, 订单号: {}", orderNo);
            // 只有支付记录真正插入成功后,才发送积分消息
            sendPointsMessage(order);
            return Result.success("支付成功");
        } catch (Exception e) {
            log.error("支付过程发生未知异常，回滚账户扣款，订单号: {}", orderNo, e);
            if(deductSuccess){
                rollbackAccountDeduct(orderNo, order.getUserId(), order.getAmount());
            }
            throw new BusinessException("支付失败: " + e.getMessage());
        }
    }



    /**
     *  库存回滚失败操作
     */
    private void rollbackStorageDeduct(String orderNo, Long productId, Integer quantity) {
        try {
            Order currentOrder = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (currentOrder != null && currentOrder.getStatus() == OrderStatus.PAID.getValue()) {
                log.warn("订单已支付, 中止库存回滚，订单号: {}", orderNo);
                return;
            }
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
     * 订单支付失败, 回滚订单状态
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
                log.warn("订单状态回滚失败(可能已被超时处理), 订单号: {}", orderNo);
            }
        } catch (Exception e) {
            log.error("订单状态回滚异常, 订单号: {}", orderNo, e);
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
                log.info("账户扣款回滚成功, 订单号: {}", orderNo);
            } else {
                log.error("账户扣款回滚失败, 需要人工介入, 订单号: {}, 原因: {}", orderNo, restoreResult.getMessage());
            }
        } catch (Exception e) {
            log.error("账户扣款回滚异常, 需要人工介入, 订单号: {}", orderNo, e);
        }
    }
}