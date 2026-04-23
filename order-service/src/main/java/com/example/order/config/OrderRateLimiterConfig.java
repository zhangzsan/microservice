
package com.example.order.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Sentinel 限流配置
 * 防止雪崩,保护系统稳定性,处理流量控制问题
 */
@Configuration
@Slf4j
public class OrderRateLimiterConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        System.setProperty("project.name", "order-service");
    }
}