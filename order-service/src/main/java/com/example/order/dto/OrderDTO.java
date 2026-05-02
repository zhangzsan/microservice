package com.example.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 返回创建订单后的订单信息,创建订单信息
 */
@Data
public class OrderDTO {
    private String orderNo;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private Integer status;

}
