package com.example.points.consumer;

import com.example.common.dto.PointsMessage;
import com.example.points.service.PointsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = "points-tx-topic", consumerGroup = "points-consumer-group")
@Slf4j
public class PointsConsumer implements RocketMQListener<PointsMessage> {

    @Autowired
    private PointsService pointsService;

    @Override
    public void onMessage(PointsMessage message) {
        log.info("接收到积分消息，订单号: {}", message.getOrderNo());
        pointsService.addPoints(message);
    }
}