package com.example.order.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.entity.Order;
import com.example.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;

@RocketMQTransactionListener
@Slf4j
public class PointsTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderNo = (String) arg;
        
        try {
            Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (order != null) {
                log.info("本地事务执行成功, 提交消息, 订单号: {}", orderNo);
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.warn("订单不存在，回滚消息，订单号: {}", orderNo);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error("本地事务检查异常，订单号: {}", orderNo, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String orderNo = msg.getHeaders().get("order_no", String.class);
        
        try {
            Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (order != null) {
                log.info("事务回查：订单存在，提交消息，订单号: {}", orderNo);
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.warn("事务回查：订单不存在，回滚消息，订单号: {}", orderNo);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error("事务回查异常，订单号: {}", orderNo, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
