package com.example.account.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户冻结记录表
 */
@Data
@TableName("t_account_frozen_log")
public class AccountFrozenLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String orderNo;
    
    private Long userId;
    
    private BigDecimal frozenAmount;
    
    private Integer status;
    
    private LocalDateTime frozenTime;
    
    private LocalDateTime unfrozenTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
