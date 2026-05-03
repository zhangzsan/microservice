package com.example.points.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    @Bean
    public RocketMQMessageConverter rocketMQMessageConverter() {
        RocketMQMessageConverter converter = new RocketMQMessageConverter();
        ObjectMapper objectMapper = converter.getMessageConverter().getObjectMapper();
        
        // 配置 Jackson 以正确处理空对象
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        return converter;
    }
}
