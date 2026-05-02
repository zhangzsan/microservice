package com.example.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 库存扣减记录表
 */
@Data
@TableName("t_storage_deduct_log")
public class StorageDeductLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String orderNo;
    
    private Long productId;
    
    private Integer deductQuantity;
    
    private Integer status;
    
    private LocalDateTime deductTime;
    
    private LocalDateTime restoreTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
