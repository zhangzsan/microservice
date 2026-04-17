package com.example.order.feign;

import com.example.common.dto.PaymentRequest;
import com.example.common.result.Result;
import com.example.order.feign.fallback.AccountFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", fallback = AccountFeignFallback.class)
public interface PaymentFeignClient {

    @PostMapping("/payment/add")
    Result<?> insertPayment(@RequestBody PaymentRequest request);

}
