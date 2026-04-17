package com.example.account.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.account.entity.Account;
import com.example.account.mapper.AccountMapper;
import com.example.common.dto.AccountDeductRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

        log.info("余额恢复成功,用户ID: {}, 金额: {}", request.getUserId(), request.getAmount());
        String idempotentKey = "stock:restore:" + request.getOrderNo() + ":" + request.getUserId();

        Boolean isFirst = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 24, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(isFirst)) {
            log.warn("余额已恢复，幂等返回，订单号: {}", request.getOrderNo());
            return;
        }

        try {
            log.info("账户服务开始恢复库存, 金额: {}, 订单号: {}", request.getAmount(), request.getOrderNo());

            int updateCount = accountMapper.restoreBalance(request.getUserId(), request.getAmount());
            if (updateCount == 0) {
                log.error("余额恢复失败, 可能已恢复过，订单号: {}", request.getOrderNo());
                throw new BusinessException("余额恢复失败");
            }

            log.info("余额恢复成功，金额: {}, 订单号: {}", request.getAmount(), request.getOrderNo());
        } catch (Exception e) {
            stringRedisTemplate.delete(idempotentKey);
            log.error("余额恢复异常，订单号: {}", request.getOrderNo(), e);
            throw e;
        }
    }
}