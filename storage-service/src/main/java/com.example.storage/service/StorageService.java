package com.example.storage.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.common.dto.StorageDeductRequest;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.exception.BusinessException;
import com.example.storage.entity.Storage;
import com.example.storage.mapper.StorageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StorageService {

    @Autowired
    private StorageMapper storageMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deduct(StorageDeductRequest request) {
        log.info("库存服务开始扣减库存，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());

        UpdateWrapper<Storage> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("product_id", request.getProductId())
                .ge("residue", request.getQuantity());

        Storage updateStorage = new Storage();
        updateStorage.setUsed(request.getQuantity());
        updateStorage.setResidue(-request.getQuantity());

        int updateCount = storageMapper.update(updateStorage, updateWrapper);
        if (updateCount == 0) {
            throw new BusinessException("库存不足或扣减失败");
        }
        log.info("库存扣减成功，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(StorageRestoreRequest request) {
        log.info("库存服务开始恢复库存，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());

        UpdateWrapper<Storage> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("product_id", request.getProductId())
                .ge("used", request.getQuantity());

        Storage updateStorage = new Storage();
        updateStorage.setUsed(-request.getQuantity());
        updateStorage.setResidue(request.getQuantity());

        int updateCount = storageMapper.update(updateStorage, updateWrapper);
        if (updateCount == 0) {
            throw new BusinessException("库存恢复失败");
        }
        log.info("库存恢复成功，商品ID: {}, 数量: {}", request.getProductId(), request.getQuantity());
    }
}