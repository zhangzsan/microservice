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
     * 恢复库存(带幂等性保护)
     * 防止重复恢复： 只有当used > 0时才允许恢复
     * 防止超额恢复：确保residue不超过total
     */
    @Update("update t_storage set used = used - #{quantity}, residue = residue + #{quantity} " +
            "where product_id = #{productId} AND used >= #{quantity} " +  // 确保已扣减的数量足够回滚
            "AND (residue + #{quantity}) <= (SELECT total FROM (select total from t_storage WHERE product_id = #{productId}) as tmp)")  // 防止超过总量
    int restore(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}