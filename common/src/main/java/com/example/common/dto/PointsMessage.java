package com.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class PointsMessage {
    private Long userId;
    private String orderNo;
    private Integer points;

    public PointsMessage(Long userId, String orderNo, Integer points) {
        this.userId = userId;
        this.orderNo = orderNo;
        this.points = points;
    }

    public PointsMessage() {
    }
}
