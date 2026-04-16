
package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.PaymentRequest;
import com.example.common.exception.BusinessException;
import com.example.common.result.Result;
import com.example.order.entity.Order;
import com.example.order.entity.PaymentRecord;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.PaymentRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PaymentService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Transactional(rollbackFor = Exception.class)
    public Result<?> processPayment(PaymentRequest request) {
        log.info("开始处理支付回调，订单号: {}, 交易号: {}", 
                request.getOrderNo(), request.getTransactionId());
        
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, request.getOrderNo())
                .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                .set(Order::getStatus, OrderStatus.PAID.getValue());
        
        int updated = orderMapper.update(null, updateWrapper);
        
        if (updated > 0) {
            savePaymentRecord(request);
            log.info("订单支付成功，订单号: {}", request.getOrderNo());
            return Result.success("支付成功");
        }
        
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, request.getOrderNo()));
        
        if (order == null) {
            log.error("订单不存在，订单号: {}", request.getOrderNo());
            return Result.error("订单不存在");
        }
        
        if (order.getStatus() == OrderStatus.PAID.getValue()) {
            log.warn("订单已支付，幂等返回，订单号: {}", request.getOrderNo());
            return Result.success("订单已支付");
        }
        
        if (order.getStatus() == OrderStatus.TIMEOUT.getValue()) {
            log.warn("订单已超时，拒绝支付，订单号: {}", request.getOrderNo());
            return Result.error("订单已超时，无法支付");
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED.getValue()) {
            log.warn("订单已取消，拒绝支付，订单号: {}", request.getOrderNo());
            return Result.error("订单已取消，无法支付");
        }
        
        log.error("订单状态异常，订单号: {}, 状态: {}", request.getOrderNo(), order.getStatus());
        return Result.error("订单状态异常，请联系客服");
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<?> payOrder(String orderNo) {
        log.info("用户发起支付，订单号: {}", orderNo);
        PaymentRequest request = new PaymentRequest();
        request.setOrderNo(orderNo);
        request.setTransactionId("TXN" + System.currentTimeMillis());
        return processPayment(request);
    }

    public Object queryOrderStatus(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        
        return order;
    }

    private void savePaymentRecord(PaymentRequest request) {
        try {
            PaymentRecord record = new PaymentRecord();
            record.setOrderNo(request.getOrderNo());
            record.setTransactionId(request.getTransactionId());
            record.setPayAmount(request.getPayAmount());
            record.setPayChannel(request.getPayChannel());
            record.setPayStatus(1);
            paymentRecordMapper.insert(record);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                log.warn("支付记录已存在，订单号: {}", request.getOrderNo());
            } else {
                throw e;
            }
        }
    }
}
