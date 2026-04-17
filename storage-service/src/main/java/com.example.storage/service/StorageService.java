package com.example.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.common.dto.StorageDeductRequest;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.exception.BusinessException;
import com.example.storage.entity.Storage;
import com.example.storage.mapper.StorageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StorageService {

    @Autowired
    private StorageMapper storageMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void deduct(StorageDeductRequest request) {
        log.info("库存服务开始扣减库存，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());

        int updateCount = storageMapper.update(request.getProductId(), request.getQuantity());
        if (updateCount == 0) {
            throw new BusinessException("库存不足或扣减失败");
        }
        log.info("库存扣减成功，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(StorageRestoreRequest request) {
        String idempotentKey = "stock:restore:" + request.getOrderNo() + ":" + request.getProductId();
        
        Boolean isFirst = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 24, TimeUnit.HOURS);
        
        if (Boolean.FALSE.equals(isFirst)) {
            log.warn("库存已恢复，幂等返回，订单号: {}", request.getOrderNo());
            return;
        }
        
        try {
            log.info("库存服务开始恢复库存, 商品ID: {}, 数量: {}, 订单号: {}", request.getProductId(), request.getQuantity(), request.getOrderNo());

            UpdateWrapper<Storage> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("product_id", request.getProductId()).ge("used", request.getQuantity());

            Storage updateStorage = new Storage();
            updateStorage.setUsed(-request.getQuantity());
            updateStorage.setResidue(request.getQuantity());

            int updateCount = storageMapper.update(updateStorage, updateWrapper);
            if (updateCount == 0) {
                log.error("库存恢复失败, 可能已恢复过，订单号: {}", request.getOrderNo());
                throw new BusinessException("库存恢复失败");
            }
            
            log.info("库存恢复成功，商品ID: {}, 数量: {}, 订单号: {}", request.getProductId(), request.getQuantity(), request.getOrderNo());
            
        } catch (Exception e) {
            stringRedisTemplate.delete(idempotentKey);
            log.error("库存恢复异常，订单号: {}", request.getOrderNo(), e);
            throw e;
        }
    }
}