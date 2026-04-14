package com.example.points.service;

import com.example.common.dto.PointsMessage;
import com.example.points.entity.PointsRecord;
import com.example.points.mapper.PointsRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PointsService {

    @Autowired
    private PointsRecordMapper pointsRecordMapper;

    public void addPoints(PointsMessage message) {
        PointsRecord record = new PointsRecord();
        record.setUserId(message.getUserId());
        record.setOrderNo(message.getOrderNo());
        record.setPoints(message.getPoints());
        pointsRecordMapper.insert(record);
        log.info("增加积分成功，用户ID: {}, 积分: {}", message.getUserId(), message.getPoints());
    }
}