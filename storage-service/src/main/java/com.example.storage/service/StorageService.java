package com.example.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.dto.StorageDeductRequest;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.exception.BusinessException;
import com.example.storage.entity.StorageDeductLog;
import com.example.storage.mapper.StorageDeductLogMapper;
import com.example.storage.mapper.StorageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class StorageService {

    @Autowired
    private StorageMapper storageMapper;

    @Autowired
    private StorageDeductLogMapper deductLogMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deduct(StorageDeductRequest request) {
        // 1. 查询该订单的库存扣减记录(精确控制订单级别的库存回滚)
        StorageDeductLog storageDeductLog = deductLogMapper.selectOne(
                new LambdaQueryWrapper<StorageDeductLog>().eq(StorageDeductLog::getOrderNo, request.getOrderNo()).eq(StorageDeductLog::getProductId, request.getProductId()));
        if (storageDeductLog != null) {
            log.error("已扣减库存扣减记录，订单号: {}, 商品ID: {}", request.getOrderNo(), request.getProductId());
            return;
        }

        log.info("库存服务开始扣减库存，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());

        int updateCount = storageMapper.update(request.getProductId(), request.getQuantity());
        if (updateCount == 0) {
            throw new BusinessException("库存不足或扣减失败");
        }

        // 创建库存扣减记录（用于后续恢复时的精确控制）
        StorageDeductLog deductLog = new StorageDeductLog();
        deductLog.setOrderNo(request.getOrderNo());
        deductLog.setProductId(request.getProductId());
        deductLog.setDeductQuantity(request.getQuantity());
        deductLog.setStatus(0);  // 0-已扣减
        deductLog.setDeductTime(LocalDateTime.now());
        deductLog.setCreatedTime(LocalDateTime.now());
        deductLog.setUpdatedTime(deductLog.getCreatedTime());

        deductLogMapper.insert(deductLog);
        log.info("库存扣减记录创建成功，订单号: {}, 商品ID: {}", request.getOrderNo(), request.getProductId());
        log.info("库存扣减成功，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(StorageRestoreRequest request) {
        String orderNo = request.getOrderNo();
        Long productId = request.getProductId();

        // 1. 查询该订单的库存扣减记录（精确控制订单级别的库存回滚）
        StorageDeductLog deductLog = deductLogMapper.selectOne(
                new LambdaQueryWrapper<StorageDeductLog>().eq(StorageDeductLog::getOrderNo, orderNo).eq(StorageDeductLog::getProductId, productId));

        if (deductLog == null) {
            log.error("未找到订单的库存扣减记录，订单号: {}, 商品ID: {}", orderNo, productId);
            throw new BusinessException("未找到库存扣减记录");
        }

        if (deductLog.getStatus() == 1) {
            log.warn("库存已恢复，幂等返回，订单号: {}, 商品ID: {}", orderNo, productId);
            return;  // 已经恢复过，直接返回
        }

        if (deductLog.getStatus() != 0) {
            log.error("库存扣减状态异常，订单号: {}, 商品ID: {}, 状态: {}", orderNo, productId, deductLog.getStatus());
            throw new BusinessException("库存扣减状态异常");
        }

        // 2. CAS更新扣减记录状态（防止重复恢复）
        int updated = deductLogMapper.markAsRestored(orderNo, productId);
        if (updated == 0) {
            log.warn("库存已被其他线程恢复，订单号: {}, 商品ID: {}", orderNo, productId);
            return;
        }

        log.info("库存服务开始恢复库存, 商品ID: {}, 数量: {}, 订单号: {}", productId, deductLog.getDeductQuantity(), orderNo);

        // 3. 恢复库存（使用订单实际扣减的数量）
        int updateCount = storageMapper.restore(productId, deductLog.getDeductQuantity());

        if (updateCount == 0) {
            log.error("库存恢复失败, 商品ID: {}, 订单号: {}", productId, orderNo);
            throw new BusinessException("库存恢复失败");
        }

        log.info("库存恢复成功，商品ID: {}, 数量: {}, 订单号: {}", productId, deductLog.getDeductQuantity(), orderNo);
    }
}
