package com.example.common.constant;

import lombok.Getter;

@Getter
public enum OperationType {
    TIMEOUT_CANCEL(1, "超时取消"),
    PAYMENT(2, "支付");

    private final int value;
    private final String desc;

    OperationType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
