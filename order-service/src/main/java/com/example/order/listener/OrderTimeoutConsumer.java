// OrderTimeoutConsumer.java
package com.example.order.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.OrderTimeoutMessage;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.order.entity.Order;
import com.example.order.feign.AccountFeignClient;
import com.example.order.feign.StorageFeignClient;
import com.example.order.mapper.OrderMapper;
import com.example.order.service.StorageCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = "order-timeout-topic", consumerGroup = "order-timeout-consumer-group")
@Slf4j
public class OrderTimeoutConsumer implements RocketMQListener<OrderTimeoutMessage> {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private StorageFeignClient storageFeignClient;
    @Autowired
    private AccountFeignClient accountFeignClient;
    @Autowired
    private StorageCacheService storageCacheService;

    @Override
    public void onMessage(OrderTimeoutMessage message) {
        log.info("接收到订单超时消息，订单号: {}", message.getOrderNo());
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, message.getOrderNo()));
        if (order != null && order.getStatus() == OrderStatus.PENDING.getValue()) {
            order.setStatus(OrderStatus.TIMEOUT.getValue());
            orderMapper.updateById(order);

            StorageRestoreRequest storageRequest = new StorageRestoreRequest();
            storageRequest.setProductId(message.getProductId());
            storageRequest.setQuantity(message.getQuantity());
            storageRequest.setOrderNo(message.getOrderNo());
            storageFeignClient.restore(storageRequest);

            AccountRestoreRequest accountRequest = new AccountRestoreRequest();
            accountRequest.setUserId(message.getUserId());
            accountRequest.setAmount(message.getAmount());
            accountRequest.setOrderNo(message.getOrderNo());
            accountFeignClient.restore(accountRequest);

            storageCacheService.restoreStockWithRedis(message.getProductId(), message.getQuantity());
            log.info("订单超时取消成功，订单号: {}", message.getOrderNo());
        }
    }
}