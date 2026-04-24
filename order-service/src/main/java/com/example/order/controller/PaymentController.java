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
        log.info("插入支付记录相关的: {}", request);
        boolean inserted = paymentService.insertPayment(request);
        if (inserted) {
            return Result.success("支付记录插入成功");
        } else {
            return Result.success("支付记录已存在(幂等)");
        }
    }
}
