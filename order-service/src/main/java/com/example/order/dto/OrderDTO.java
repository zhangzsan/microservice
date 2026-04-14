package com.example.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDTO {
    private String orderNo;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private Integer status;

}
