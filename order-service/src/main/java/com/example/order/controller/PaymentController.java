package com.example.order.controller;

import com.example.common.dto.PaymentRequest;
import com.example.common.result.Result;
import com.example.order.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@Slf4j
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/pay/{orderNo}")
    public Result<?> payOrder(@PathVariable String orderNo) {
        log.info("接收到支付请求，订单号: {}", orderNo);
        return paymentService.payOrder(orderNo);
    }

    @PostMapping("/callback")
    public Result<?> paymentCallback(@RequestBody PaymentRequest request) {
        log.info("接收到支付平台回调，订单号: {}, 交易号: {}", 
                request.getOrderNo(), request.getTransactionId());
        return paymentService.processPayment(request);
    }

    @GetMapping("/status/{orderNo}")
    public Result<?> queryOrderStatus(@PathVariable String orderNo) {
        log.info("查询订单状态，订单号: {}", orderNo);
        return Result.success(paymentService.queryOrderStatus(orderNo));
    }
}
