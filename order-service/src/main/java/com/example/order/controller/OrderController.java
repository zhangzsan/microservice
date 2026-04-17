package com.example.order.controller;

import com.example.common.dto.OrderCreateRequest;
import com.example.common.result.Result;
import com.example.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<?> createOrder(@RequestBody OrderCreateRequest request) {
        log.info("创建订单, 请求参数: {}", request);
        return Result.success(orderService.createOrder(request));
    }

    @PostMapping("/pay/{orderNo}")
    public Result<?> payOrder(@PathVariable String orderNo) {
        log.info("用户发起支付, 订单号: {}", orderNo);
        return orderService.payOrder(orderNo);
    }

    @GetMapping("/status/{orderNo}")
    public Result<?> queryOrderStatus(@PathVariable String orderNo) {
        log.info("查询订单状态, 订单号: {}", orderNo);
        return Result.success(orderService.queryOrderStatus(orderNo));
    }
}