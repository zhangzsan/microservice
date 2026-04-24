package com.example.order.service;

import com.example.common.dto.PaymentRequest;
import com.example.common.exception.BusinessException;
import com.example.common.result.Result;
import com.example.order.entity.PaymentRecord;
import com.example.order.mapper.PaymentRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class PaymentService {

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    /**
     * 插入支付记录
     * @return true-插入成功, false-记录已存在(幂等)
     */
    public boolean insertPayment(PaymentRequest request) {
        try {
            PaymentRecord record = new PaymentRecord();
            record.setOrderNo(request.getOrderNo());
            record.setTransactionId(request.getTransactionId());
            record.setPayAmount(request.getPayAmount());
            record.setPayChannel(request.getPayChannel());
            record.setPayStatus(1); // 1-支付成功
            record.setCreatedTime(LocalDateTime.now());
            record.setUpdatedTime(LocalDateTime.now());
            paymentRecordMapper.insert(record);
            log.info("支付记录插入成功, 订单号: {}", request.getOrderNo());
            return true;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                log.warn("支付记录已存在(幂等), 订单号: {}", request.getOrderNo());
                return false; // 返回false表示记录已存在
            } else {
                log.error("支付记录插入失败, 订单号: {}", request.getOrderNo(), e);
                throw new BusinessException("支付记录添加失败: " + e.getMessage());
            }
        }
    }
}
