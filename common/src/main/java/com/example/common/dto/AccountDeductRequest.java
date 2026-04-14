package com.example.common.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户扣款的请求参数
 */
@Data
public class AccountDeductRequest {
    private Long userId;
    private BigDecimal amount;
    private String orderNo;
}