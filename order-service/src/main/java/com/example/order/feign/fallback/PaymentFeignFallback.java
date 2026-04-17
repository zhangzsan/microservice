package com.example.order.feign.fallback;

import com.example.common.dto.PaymentRequest;
import com.example.common.result.Result;
import com.example.order.feign.PaymentFeignClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentFeignFallback implements PaymentFeignClient {
    @Override
    public Result<?> insertPayment(PaymentRequest request) {
        return Result.error("支付失败,请重试");
    }
}
