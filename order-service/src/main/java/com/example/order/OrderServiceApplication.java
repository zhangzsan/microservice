package com.example.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.yaml.snakeyaml.nodes.CollectionNode;

import javax.naming.Context;

@SpringBootApplication
@MapperScan("com.example.order.mapper")
@EnableScheduling
@EnableFeignClients(basePackages = "com.example.order.feign")
public class OrderServiceApplication {
    public static void main(String[] args) {
//        SpringApplication.run(OrderServiceApplication.class, args);
        ConfigurableApplicationContext context = SpringApplication.run(OrderServiceApplication.class, args);

        // 打印 Sentinel 配置，确认 Spring 是否读到了
        String dashboard = context.getEnvironment().getProperty("spring.cloud.sentinel.transport.dashboard");
        System.err.println("==========================================");
        System.err.println("Sentinel Dashboard Addr from YAML: " + dashboard);
        System.err.println("==========================================");
    }



}
