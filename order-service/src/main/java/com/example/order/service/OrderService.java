package com.example.order.service;

import com.example.common.constant.OrderStatus;
import com.example.common.dto.*;
import com.example.common.exception.BusinessException;
import com.example.common.result.Result;
import com.example.order.dto.OrderDTO;
import com.example.order.entity.Order;
import com.example.order.feign.AccountFeignClient;
import com.example.order.feign.StorageFeignClient;
import com.example.order.mapper.OrderMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(OrderCreateRequest request) {
        log.info("开始创建订单，用户ID: {}, 商品ID: {}, 数量: {}", request.getUserId(), request.getProductId(), request.getQuantity());

        // 1. Redis预扣库存
        boolean cacheDeductSuccess = storageCacheService.deductStockWithRedis(
                request.getProductId(), request.getQuantity());
        if (!cacheDeductSuccess) {
            throw new BusinessException("库存不足,请选择其他商品下单");
        }

        String orderNo = generateOrderNo();

        // 2. 调用库存服务扣减库存
        StorageDeductRequest storageRequest = new StorageDeductRequest();
        storageRequest.setProductId(request.getProductId());
        storageRequest.setQuantity(request.getQuantity());
        storageRequest.setOrderNo(orderNo);
        Result<?> storageResult = storageFeignClient.deduct(storageRequest);
        if (!storageResult.isSuccess()) {
            storageCacheService.restoreStockWithRedis(request.getProductId(), request.getQuantity());
            throw new BusinessException(storageResult.getMessage());
        }

        // 3. 调用账户服务扣减余额
        AccountDeductRequest accountRequest = new AccountDeductRequest();
        accountRequest.setUserId(request.getUserId());
        accountRequest.setAmount(request.getAmount());
        accountRequest.setOrderNo(orderNo);
        Result<?> accountResult = accountFeignClient.deduct(accountRequest);
        if (!accountResult.isSuccess()) {
            throw new BusinessException(accountResult.getMessage());
        }

        // 4. 保存订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.PENDING.getValue());
        orderMapper.insert(order);

        // 5. 发送积分事务消息
        sendPointsMessage(order);

        // 6. 发送订单超时延迟消息(30分钟后)
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
        rocketMQTemplate.sendMessageInTransaction("points-topic", message, order.getOrderNo());
        log.info("发送积分事务消息，订单号: {}", order.getOrderNo());
    }

    private void sendOrderTimeoutMessage(Order order) {
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
}