package com.example.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.account.entity.AccountFrozenLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountFrozenLogMapper extends BaseMapper<AccountFrozenLog> {
    
    /**
     * 标记订单冻结记录为已解冻(带幂等性保护)
     * 只有状态为"已冻结"的记录才能被解冻，防止重复解冻
     */
    @Update("UPDATE t_account_frozen_log " +
            "SET status = 1, unfrozen_time = NOW() " +
            "WHERE order_no = #{orderNo} " +
            "AND status = 0")  // CAS条件：只能解冻未解冻的记录
    int markAsUnfrozen(@Param("orderNo") String orderNo);
}
