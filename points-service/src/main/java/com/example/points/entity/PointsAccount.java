package com.example.points.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("points_account")
public class PointsAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    /**
     * 总积分(累计获得的积分)
     */
    private Integer totalPoints;
    
    /**
     * 可用积分(可以用于消费的积分)
     */
    private Integer availablePoints;
    
    /**
     * 冻结积分(暂时不可用的积分)
     */
    private Integer frozenPoints;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
