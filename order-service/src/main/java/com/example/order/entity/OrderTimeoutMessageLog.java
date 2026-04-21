package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单超时消息幂等日志表
 */
@Data
@TableName("t_order_timeout_message_log")
public class OrderTimeoutMessageLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String orderNo;
    
    private String messageId;
    
    private Integer processed;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
