
package com.example.order.config;


import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 添加限流保护(防止雪崩)
 */
@Configuration
public class OrderRateLimiterConfig {

    @Bean
    public RateLimiter timeoutProcessRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(50)
            .timeoutDuration(Duration.ofMillis(100))
            .build();

        return RateLimiter.of("timeout-process", config);
    }
}
