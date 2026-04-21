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

    /**
     *  创建幂等性订单尚未控制
     */
    @PostMapping("/create")
    public Result<?> createOrder(@RequestBody OrderCreateRequest request) {
        return Result.success(orderService.createOrder(request));
    }

    /**
     * 预生成订单号(用于防重复提交)
     * 前端进入下单页时调用,获取订单号后在提交时携带
     */
    @GetMapping("/pre-generate-order-no")
    public Result<String> preGenerateOrderNo() {
        String orderNo = orderService.generateAndReserveOrderNo();
        log.info("预生成订单号: {}", orderNo);
        return Result.success(orderNo);
    }

    /**
     *  支付订单
     */
    @PostMapping("/pay/{orderNo}")
    public Result<?> payOrder(@PathVariable String orderNo) {
        log.info("用户发起支付, 订单号: {}", orderNo);
        return orderService.payOrder(orderNo);
    }

    /**
     * 查询订单的状态
     */
    @GetMapping("/status/{orderNo}")
    public Result<?> queryOrderStatus(@PathVariable String orderNo) {
        log.info("查询订单状态, 订单号: {}", orderNo);
        return Result.success(orderService.queryOrderStatus(orderNo));
    }
}