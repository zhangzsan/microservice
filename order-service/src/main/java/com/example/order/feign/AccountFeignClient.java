package com.example.order.feign;

import com.example.common.dto.AccountDeductRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.result.Result;
import com.example.order.feign.fallback.AccountFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 远程调用的方法
 */
@FeignClient(name = "account-service", fallback = AccountFeignFallback.class)
public interface AccountFeignClient {
    @PostMapping("/account/deduct")
    Result<?> deduct(@RequestBody AccountDeductRequest request);

    @PostMapping("/account/restore")
    Result<?> restore(@RequestBody AccountRestoreRequest request);
}