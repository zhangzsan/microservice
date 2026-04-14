package com.example.order.controller;

import com.example.common.dto.OrderCreateRequest;
import com.example.common.result.Result;
import com.example.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.bind.annotation.PostMapping;import org.springframework.web.bind.annotation.RequestBody;import org.springframework.web.bind.annotation.RequestMapping;import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<?> createOrder(@RequestBody OrderCreateRequest request) {
        return Result.success(orderService.createOrder(request));
    }
}