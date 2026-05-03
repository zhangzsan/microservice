package com.example.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.account.entity.AccountFrozenLog;
import com.example.account.mapper.AccountFrozenLogMapper;
import com.example.account.mapper.AccountMapper;
import com.example.common.dto.AccountDeductRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFrozenLogMapper frozenLogMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deduct(AccountDeductRequest request) {
        AccountFrozenLog frozen = frozenLogMapper.selectOne(new LambdaQueryWrapper<AccountFrozenLog>().eq(AccountFrozenLog::getOrderNo, request.getOrderNo()));
        if (frozen != null) {
            log.info("已经支付过了,无需重复支付订单");
            return;
        }
        int updateCount = accountMapper.updateBalance(request.getUserId(), request.getAmount());
        if (updateCount == 0) {
            throw new BusinessException("余额不足或扣减失败");
        }
        // 创建账户冻结记录(用于后续恢复时的精确控制)
        AccountFrozenLog frozenLog = new AccountFrozenLog();
        frozenLog.setOrderNo(request.getOrderNo());
        frozenLog.setUserId(request.getUserId());
        frozenLog.setFrozenAmount(request.getAmount());
        frozenLog.setStatus(0);  // 0-已冻结
        frozenLog.setFrozenTime(LocalDateTime.now());
        frozenLog.setCreatedTime(LocalDateTime.now());
        frozenLog.setUpdatedTime(frozenLog.getCreatedTime());
        int insert = frozenLogMapper.insert(frozenLog);
        log.info("账户冻结记录创建成功，订单号: {}, 用户ID: {}", request.getOrderNo(), request.getUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(AccountRestoreRequest request) {
        String orderNo = request.getOrderNo();

        // 1. 查询该订单的冻结记录(精确控制订单级别的资金回滚)
        AccountFrozenLog frozenLog = frozenLogMapper.selectOne(new LambdaQueryWrapper<AccountFrozenLog>().eq(AccountFrozenLog::getOrderNo, orderNo));

        if (frozenLog == null) {
            log.error("未找到订单的冻结记录,订单号: {}", orderNo);
            throw new BusinessException("未找到订单冻结记录");
        }

        if (frozenLog.getStatus() == 1) {
            log.warn("订单已解冻，幂等返回，订单号: {}", orderNo);
            return;  // 已经解冻过, 直接返回
        }

        if (frozenLog.getStatus() != 0) {
            log.error("订单冻结状态异常，订单号: {}, 状态: {}", orderNo, frozenLog.getStatus());
            throw new BusinessException("订单冻结状态异常");
        }

        // 2. CAS更新冻结记录状态(防止重复解冻)
        int updated = frozenLogMapper.markAsUnfrozen(orderNo);
        if (updated == 0) {
            log.warn("订单已被其他线程解冻，订单号: {}", orderNo);
            return;
        }

        log.info("账户服务开始恢复, 金额: {}, 订单号: {}", frozenLog.getFrozenAmount(), orderNo);
        // 3. 恢复余额(使用订单实际冻结的金额)
        int updateCount = accountMapper.restoreBalance(frozenLog.getUserId(), frozenLog.getFrozenAmount());
        if (updateCount == 0) {
            log.error("余额恢复失败, 用户ID: {}, 订单号: {}", frozenLog.getUserId(), orderNo);
            throw new BusinessException("余额恢复失败");
        }

        log.info("余额恢复成功并设置幂等标记，金额: {}, 订单号: {}", frozenLog.getFrozenAmount(), orderNo);
    }
}