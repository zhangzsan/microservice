package com.example.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.storage.entity.Storage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StorageMapper extends BaseMapper<Storage> {

    /**
     * 扣减库存,扣减key,防止库存扣减为负数
     */
    @Update("update t_storage set used = used + #{quantity}, residue = residue - #{quantity} where product_id = #{productId} and residue>= #{quantity}")
    int update(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     *
     * 恢复库存
     */
    @Update("update t_storage set used = used - #{quantity}, residue = residue + #{quantity} where product_id = #{productId}")
    int restore(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}