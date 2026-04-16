package com.example.account.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.account.entity.Account;
import com.example.account.mapper.AccountMapper;
import com.example.common.dto.AccountDeductRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deduct(AccountDeductRequest request) {

        int updateCount = accountMapper.updateBalance(request.getUserId(), request.getAmount());
        if (updateCount == 0) {
            throw new BusinessException("余额不足或扣减失败");
        }
        log.info("余额扣减成功, 用户ID: {}, 金额: {}", request.getUserId(), request.getAmount());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(AccountRestoreRequest request) {
        UpdateWrapper<Account> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("user_id", request.getUserId());

        Account account = new Account();
        account.setBalance(account.getBalance().add(request.getAmount()));

        int updateCount = accountMapper.update(account, updateWrapper);
        if (updateCount == 0) {
            throw new BusinessException("余额恢复失败");
        }
        log.info("余额恢复成功，用户ID: {}, 金额: {}", request.getUserId(), request.getAmount());
    }
}