NEW_FILE_CODE
package com.example.common.constant;

import lombok.Getter;

@Getter
public enum OperationStatus {
    PROCESSING(0, "处理中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final int value;
    private final String desc;

    OperationStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
