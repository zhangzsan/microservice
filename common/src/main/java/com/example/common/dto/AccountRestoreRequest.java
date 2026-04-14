package com.example.common.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户恢复请求
 */
@Data
public class AccountRestoreRequest {
    private Long userId;
    private BigDecimal amount;
    private String orderNo;
}