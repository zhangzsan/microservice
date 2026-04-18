package com.example.points.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.dto.PointsMessage;
import com.example.points.entity.PointsRecord;
import com.example.points.mapper.PointsRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class PointsService {

    @Autowired
    private PointsRecordMapper pointsRecordMapper;

    @Transactional(rollbackFor = Exception.class)
    public void addPoints(PointsMessage message) {
        log.info("Consumer收到加积分的请求了");
        PointsRecord existingRecord = pointsRecordMapper.selectOne(
                new LambdaQueryWrapper<PointsRecord>().eq(PointsRecord::getOrderNo, message.getOrderNo()));

        if (existingRecord != null) {
            log.warn("积分记录已存在，跳过重复插入，订单号: {}", message.getOrderNo());
            return;
        }
        log.info("开始加积分了,积分详情: {}", message);
        PointsRecord record = new PointsRecord();
        record.setUserId(message.getUserId());
        record.setOrderNo(message.getOrderNo());
        record.setPoints(message.getPoints());
        record.setCreateTime(LocalDateTime.now());
        try {
            pointsRecordMapper.insert(record);
            log.info("增加积分成功，用户ID: {}, 积分: {}", message.getUserId(), message.getPoints());
        } catch (Exception e) {
            log.error("插入积分记录失败，订单号: {}", message.getOrderNo(), e);
            throw e;
        }
    }
}