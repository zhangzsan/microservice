package com.example.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.account.entity.Account;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface AccountMapper extends BaseMapper<Account> {

    @Update("update t_account set balance = balance - #{amount} where user_id = #{userId} and balance >=#{amount}")
    int updateBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 恢复余额（带幂等性保护）
     * 防止超额恢复：确保恢复后的余额不超过 (原余额 + frozen)
     * 注意：这里假设订单支付时已经扣减了balance并增加了frozen
     */
    @Update("update t_account set balance = balance + #{amount}, frozen = frozen - #{amount} " +
            "where user_id = #{userId} " +
            "AND frozen >= #{amount}")  // 确保有足够的冻结金额可以回滚
    int restoreBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
