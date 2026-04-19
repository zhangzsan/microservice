package com.example.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.yaml.snakeyaml.nodes.CollectionNode;

import javax.naming.Context;

@SpringBootApplication(scanBasePackages = {"com.example.order", "com.example.common"})
@MapperScan("com.example.order.mapper")
@EnableScheduling   //启动定时任务的配置
@EnableAsync        //启用异步支持
@EnableFeignClients(basePackages = "com.example.order.feign")
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
