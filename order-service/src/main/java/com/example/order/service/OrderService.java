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
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Autowired
    private RedissonClient redissonClient;

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(OrderCreateRequest request) {
        //1. redis预扣减库存
        log.info("开始创建订单, 用户ID: {}, 商品ID: {}, 数量: {}", request.getUserId(), request.getProductId(), request.getQuantity());
        boolean cacheDeductSuccess = storageCacheService.deductStockWithRedis(request.getProductId(), request.getQuantity());
        if (!cacheDeductSuccess) {
            throw new BusinessException("库存不足,请选择其他商品下单");
        }
        String orderNo = generateOrderNo();

        //2.调用库存服务扣减库存
        StorageDeductRequest storageRequest = new StorageDeductRequest();
        storageRequest.setProductId(request.getProductId());
        storageRequest.setQuantity(request.getQuantity());
        storageRequest.setOrderNo(orderNo);
        Result<?> storageResult = storageFeignClient.deduct(storageRequest);
        if (!storageResult.isSuccess()) {
            storageCacheService.restoreStockWithRedis(request.getProductId(), request.getQuantity(), orderNo);
            throw new BusinessException(storageResult.getMessage());
        }

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
        log.info("订单创建成功，订单号: {}", orderNo);
        return convertToDTO(order);
    }

    private void sendPointsMessage(Order order) {
        PointsMessage pointsMessage = new PointsMessage();
        pointsMessage.setUserId(order.getUserId());
        pointsMessage.setOrderNo(order.getOrderNo());
        pointsMessage.setPoints(order.getAmount().intValue());

        Message<PointsMessage> message = MessageBuilder.withPayload(pointsMessage)
                .setHeader("order_no", order.getOrderNo())
                .build();
        rocketMQTemplate.convertAndSend("points-topic", message);
        log.info("发送积分事务消息, 订单号: {}", order.getOrderNo());
    }

    private void sendOrderTimeoutMessage(Order order) {
        log.info("开始发送订单超时延迟消息，订单号: {}", order.getOrderNo());
        OrderTimeoutMessage message = new OrderTimeoutMessage();
        message.setOrderNo(order.getOrderNo());
        message.setProductId(order.getProductId());
        message.setQuantity(order.getQuantity());
        message.setUserId(order.getUserId());
        message.setAmount(order.getAmount());
        rocketMQTemplate.syncSend("order-timeout-topic",
                MessageBuilder.withPayload(message).build(),
                3000, 16); // 延迟级别16对应30分钟
        log.info("发送订单超时延迟消息，订单号: {}", order.getOrderNo());
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
     * 调用支付订单
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

    //    @GlobalTransactional(name = "pay-order-tx", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Result<?> doPayOrder(String orderNo) {
        log.info("开始调用支付服务,订单号: {}", orderNo);
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));

        if (order == null) {
            log.error("订单不存在, 订单号: {}", orderNo);
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == OrderStatus.PAID.getValue()) {
            log.info("订单已支付, 幂等返回,订单号: {}", orderNo);
            return Result.success("订单已支付");
        }
        if (order.getStatus() == OrderStatus.CANCELLED.getValue()) {
            log.warn("订单已取消, 订单号: {}", orderNo);
            throw new BusinessException("订单已取消");
        }
        if (order.getStatus() == OrderStatus.TIMEOUT.getValue()) {
            log.warn("订单已超时, 订单号: {}", orderNo);
            throw new BusinessException("订单已超时");
        }
        if (order.getStatus() != OrderStatus.PENDING.getValue()) {
            log.error("订单状态异常, 订单号: {}, 状态: {}", orderNo, order.getStatus());
            throw new BusinessException("订单状态异常");
        }

        UpdateWrapper<Order> update = new UpdateWrapper<>();
        update.lambda().eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                .set(Order::getStatus, OrderStatus.PAID.getValue());

        int updated = orderMapper.update(null, update);
        if (updated == 0) {
            log.warn("订单状态更新失败,可能已被其他线程处理, 订单号: {}", orderNo);
            throw new BusinessException("订单状态更新失败");
        }

        AccountDeductRequest request = new AccountDeductRequest();
        request.setOrderNo(orderNo);
        request.setUserId(order.getUserId());
        request.setAmount(order.getAmount());
        Result<?> deduct = accountFeignClient.deduct(request);
        if (!deduct.isSuccess()) {
            log.error("扣除用户余额失败, 订单号: {}, 原因: {}", orderNo, deduct.getMessage());
            throw new BusinessException("扣款失败: " + deduct.getMessage());
        }

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderNo(orderNo);
        paymentRequest.setTransactionId("TXN" + System.currentTimeMillis() + orderNo);
        paymentRequest.setPayAmount(order.getAmount());
        paymentRequest.setPayChannel(1);
        Result<?> result = paymentFeignClient.insertPayment(paymentRequest);
        if (!result.isSuccess()) {
            log.error("添加支付信息失败, 订单号: {}, 原因: {}", orderNo, result.getMessage());
            throw new BusinessException("支付记录创建失败: " + result.getMessage());
        }

        log.info("支付成功, 订单号: {}", orderNo);
        sendPointsMessage(order);
        return Result.success("支付成功");
    }
}