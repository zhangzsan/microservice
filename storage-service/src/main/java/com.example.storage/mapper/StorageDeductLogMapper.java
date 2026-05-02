package com.example.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.storage.entity.StorageDeductLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StorageDeductLogMapper extends BaseMapper<StorageDeductLog> {
    
    /**
     * 标记库存扣减记录为已恢复（带幂等性保护）
     * 只有状态为"已扣减"的记录才能被恢复，防止重复恢复
     */
    @Update("UPDATE t_storage_deduct_log " +
            "SET status = 1, restore_time = NOW() " +
            "WHERE order_no = #{orderNo} " +
            "AND product_id = #{productId} " +
            "AND status = 0")  // CAS条件：只能恢复未恢复的记录
    int markAsRestored(@Param("orderNo") String orderNo, @Param("productId") Long productId);
}
