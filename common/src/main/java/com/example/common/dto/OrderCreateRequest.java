package com.example.common.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求
 */
@Data
public class OrderCreateRequest {
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private String orderNo; // 前端传递的预生成订单号(可选)
}