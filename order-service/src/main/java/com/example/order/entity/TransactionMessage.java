package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 本地事务表,事务消息没有发送成功后,记录到本表中进行后续补偿
 */
@Data
@TableName("t_transaction_message")
public class TransactionMessage {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String messageId;
    
    private String topic;
    
    private String tag;
    
    private String messageBody;
    
    private Integer status;
    
    private Integer retryCount;
    
    private Integer maxRetryCount;
    
    private LocalDateTime nextRetryTime;
    
    private String errorMessage;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
