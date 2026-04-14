package com.example.common.dto;

import lombok.Data;

/**
 * 库存恢复的请求参数
 */
@Data
public class StorageRestoreRequest {
    private Long productId;
    private Integer quantity;
    private String orderNo;
}