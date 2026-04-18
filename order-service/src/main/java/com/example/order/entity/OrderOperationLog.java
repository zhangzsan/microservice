
package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单补偿事务表
 */
@Data
@TableName("t_order_operation_log")
public class OrderOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer operationType;
    private Integer operationStatus;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String requestData;
    private String errorMessage;
    private LocalDateTime nextRetryTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
