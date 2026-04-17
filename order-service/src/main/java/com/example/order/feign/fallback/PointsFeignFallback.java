package com.example.order.feign.fallback;

import com.example.common.dto.PointsMessage;
import com.example.common.result.Result;
import com.example.order.feign.PointsFeignClient;
import org.springframework.stereotype.Component;

@Component
public class PointsFeignFallback implements PointsFeignClient {

    @Override
    public Result<?> addPoints(PointsMessage pointsMessage) {
        // 降级处理：记录日志，不阻塞主流程
        return Result.error("积分服务异常，积分增加失败");
    }
}