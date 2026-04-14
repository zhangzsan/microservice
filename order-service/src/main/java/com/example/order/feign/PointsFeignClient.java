package com.example.order.feign;

import com.example.common.dto.PointsMessage;
import com.example.common.result.Result;
import com.example.order.feign.fallback.PointsFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "points-service", fallback = PointsFeignFallback.class)
public interface PointsFeignClient {

    @PostMapping("/points/add")
    Result<?> addPoints(@RequestBody PointsMessage pointsMessage);
}