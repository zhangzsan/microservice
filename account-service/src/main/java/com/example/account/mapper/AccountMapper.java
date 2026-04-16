package com.example.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.account.entity.Account;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface AccountMapper extends BaseMapper<Account> {

    @Update("update t_account set balance = balance - #{amount} where user_id = #{userId} and balance>=0")
    int updateBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
