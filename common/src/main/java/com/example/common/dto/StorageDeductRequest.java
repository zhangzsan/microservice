package com.example.common.dto;

import lombok.Data;

@Data
public class StorageDeductRequest {
    private Long productId;
    private Integer quantity;
    private String orderNo;
}