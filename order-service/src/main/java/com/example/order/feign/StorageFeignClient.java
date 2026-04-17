package com.example.order.feign;

import com.example.common.dto.StorageDeductRequest;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.result.Result;
import com.example.order.feign.fallback.StorageFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "storage-service",fallback = StorageFeignFallback.class)
public interface StorageFeignClient {
    @PostMapping("/storage/deduct")
    Result<?> deduct(@RequestBody StorageDeductRequest request);

    @PostMapping("/storage/restore")
    Result<?> restore(@RequestBody StorageRestoreRequest request);
}
