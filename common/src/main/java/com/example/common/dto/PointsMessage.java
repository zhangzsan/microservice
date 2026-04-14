package com.example.common.dto;

import lombok.Data;

/**
 *
 */
@Data
public class PointsMessage {
    private Long userId;
    private String orderNo;
    private Integer points;
}
