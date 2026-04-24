package com.example.points.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.common.dto.PointsMessage;
import com.example.points.entity.PointsAccount;
import com.example.points.entity.PointsRecord;
import com.example.points.mapper.PointsAccountMapper;
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

    @Autowired
    private PointsAccountMapper pointsAccountMapper;

    @Transactional(rollbackFor = Exception.class)
    public void addPoints(PointsMessage message) {
        log.info("Consumer收到加积分的请求了");

        // 1. 幂等性检查:通过orderNo判断是否已处理
        PointsRecord existingRecord = pointsRecordMapper.selectOne(new LambdaQueryWrapper<PointsRecord>().eq(PointsRecord::getOrderNo, message.getOrderNo()));

        if (existingRecord != null) {
            log.warn("积分记录已存在，跳过重复插入，订单号: {}", message.getOrderNo());
            return;
        }

        log.info("开始加积分了,积分详情: {}", message);

        // 2. 插入积分记录
        PointsRecord record = new PointsRecord();
        record.setUserId(message.getUserId());
        record.setOrderNo(message.getOrderNo());
        record.setPoints(message.getPoints());
        record.setCreateTime(LocalDateTime.now());
        pointsRecordMapper.insert(record);

        // 3. 确保积分账户存在(如果不存在则初始化)
        pointsAccountMapper.initAccountIfNotExists(message.getUserId());

        // 4. 原子性更新积分账户余额
        int affectedRows = pointsAccountMapper.increasePoints(message.getUserId(), message.getPoints());
        if (affectedRows == 0) {
            log.error("更新积分账户失败，用户ID: {}", message.getUserId());
            throw new RuntimeException("更新积分账户失败");
        }

        log.info("增加积分成功，用户ID: {}, 订单号: {}, 积分: {}", message.getUserId(), message.getOrderNo(), message.getPoints());
    }

    //根据订单号查找是否已经添加积分
    public boolean checkPointsRecordExists(String orderNo) {
        LambdaQueryWrapper<PointsRecord> queryWrapper = new LambdaQueryWrapper<PointsRecord>().eq(PointsRecord::getOrderNo, orderNo);
        return pointsRecordMapper.selectOne(queryWrapper) != null;
    }
}