package com.example.order.service;

import com.example.common.dto.PaymentRequest;
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

    public Result<?> insertPayment(PaymentRequest request) {
        try {
            PaymentRecord record = new PaymentRecord();
            record.setOrderNo(request.getOrderNo());
            record.setTransactionId(request.getTransactionId());
            record.setPayAmount(request.getPayAmount());
            record.setPayChannel(request.getPayChannel());
            record.setPayStatus(1);
            record.setCreatedTime(LocalDateTime.now());
            record.setUpdatedTime(record.getUpdatedTime());
            int insert = paymentRecordMapper.insert(record);
            if (insert > 0) {
                return Result.success();
            } else {
                return Result.error("支付记录添加失败");
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                log.warn("支付记录已存在，订单号: {}", request.getOrderNo());
                return Result.success();
            } else {
                return Result.error("支付记录添加失败: " + e.getMessage());
            }
        }
    }
}
