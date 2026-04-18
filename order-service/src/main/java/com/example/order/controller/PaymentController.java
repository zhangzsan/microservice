package com.example.order.controller;


import com.example.common.dto.PaymentRequest;
import com.example.common.result.Result;
import com.example.order.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
@Slf4j
public class PaymentController {


    @Autowired
    private PaymentService paymentService;

    @PostMapping("/add")
    public Result<?> insertPayment(@RequestBody PaymentRequest request) {
        log.info("开始处理支付订单: {}", request);
        paymentService.insertPayment(request);
        return Result.success();
    }
}
