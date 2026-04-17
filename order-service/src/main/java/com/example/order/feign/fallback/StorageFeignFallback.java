package com.example.order.feign.fallback;

import com.example.common.dto.StorageDeductRequest;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.result.Result;
import com.example.order.feign.StorageFeignClient;
import org.springframework.stereotype.Component;

@Component
public class StorageFeignFallback implements StorageFeignClient {
    @Override
    public Result<?> deduct(StorageDeductRequest request) {
        return Result.error("库存服务异常，请稍后重试");
    }

    @Override
    public Result<?> restore(StorageRestoreRequest request) {
        Result.error("库存服务异常");
    }
}