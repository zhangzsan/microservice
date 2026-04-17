package com.example.order.listener;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.OrderTimeoutMessage;
import com.example.order.entity.Order;
import com.example.order.mapper.OrderMapper;
import com.example.order.service.ResourceRollbackService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RocketMQMessageListener(topic = "order-timeout-topic", consumerGroup = "order-timeout-consumer-group")
@Slf4j
public class OrderTimeoutConsumer implements RocketMQListener<OrderTimeoutMessage> {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ResourceRollbackService rollbackService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(OrderTimeoutMessage message) {
        log.info("接收到订单超时消息,订单号: {}", message.getOrderNo());
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, message.getOrderNo())
                .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                .set(Order::getStatus, OrderStatus.TIMEOUT.getValue());

        //数据库的cas操作控制重复操作
        int updated = orderMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("订单状态更新为超时成功，创建回滚任务，订单号: {}", message.getOrderNo());
            rollbackService.createRollbackTask(message);
            log.info("订单超时处理完成，回滚任务已创建，订单号: {}", message.getOrderNo());
        } else {
            log.warn("订单状态已变更或不存在，无需处理，订单号: {}", message.getOrderNo());
        }
    }
}