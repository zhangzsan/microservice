package com.example.order.constant;

import lombok.Getter;

@Getter
public enum MessageStatus {
    
    PENDING(0, "待发送"),
    SENT(1, "已发送"),
    CONFIRMED(2, "已确认"),
    FAILED(3, "失败");
    
    private final int value;
    private final String desc;
    
    MessageStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
