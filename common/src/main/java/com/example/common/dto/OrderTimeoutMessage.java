package com.example.common.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 处理超时订单
 */
@Data
public class OrderTimeoutMessage {
    private String orderNo;
    private Long productId;
    private Integer quantity;
    private Long userId;
    private BigDecimal amount;
}