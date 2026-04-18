package com.example.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 库存记录表
 */
@Data
@TableName("t_storage")
public class Storage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Integer total;
    private Integer used;
    private Integer residue;
    @Version
    private Integer version;
}