// PointsTransactionListener.java
package com.example.order.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.PointsMessage;
import com.example.common.result.Result;
import com.example.order.entity.Order;
import com.example.order.feign.PointsFeignClient;
import com.example.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RocketMQTransactionListener
@Slf4j
public class PointsTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PointsFeignClient pointsFeignClient;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderNo = (String) arg;
        log.info("执行本地事务，订单号: {}", orderNo);
        try {
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (order != null && order.getStatus() == OrderStatus.PAID.getValue()) {
                PointsMessage pointsMessage = (PointsMessage) msg.getPayload();
                Result<?> result = pointsFeignClient.addPoints(pointsMessage);
                if (result.isSuccess()) {
                    return RocketMQLocalTransactionState.COMMIT;
                } else {
                    return RocketMQLocalTransactionState.ROLLBACK;
                }
            } else {
                return RocketMQLocalTransactionState.UNKNOWN;
            }
        } catch (Exception e) {
            log.error("本地事务执行异常", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String orderNo = (String) msg.getHeaders().get("order_no");
        log.info("回查事务状态，订单号: {}", orderNo);
        try {
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (order == null) return RocketMQLocalTransactionState.ROLLBACK;
            if (order.getStatus() == OrderStatus.PAID.getValue()) {
                return RocketMQLocalTransactionState.COMMIT;
            } else if (order.getStatus() == OrderStatus.CANCELLED.getValue() ||
                    order.getStatus() == OrderStatus.TIMEOUT.getValue()) {
                return RocketMQLocalTransactionState.ROLLBACK;
            } else {
                return RocketMQLocalTransactionState.UNKNOWN;
            }
        } catch (Exception e) {
            log.error("回查事务状态异常", e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
