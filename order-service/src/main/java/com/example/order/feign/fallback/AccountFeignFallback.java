package com.example.order.feign.fallback;

import com.example.common.dto.AccountDeductRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.result.Result;
import com.example.order.feign.AccountFeignClient;
import org.springframework.stereotype.Component;

@Component
public class AccountFeignFallback implements AccountFeignClient {
    @Override
    public Result<?> deduct(AccountDeductRequest request) {
        return Result.error("账户服务异常，请稍后重试");
    }

    @Override
    public Result<?> restore(AccountRestoreRequest request) {
        return Result.error("账户服务异常");
    }
}