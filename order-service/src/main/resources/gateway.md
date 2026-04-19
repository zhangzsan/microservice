server:
port: 8080

spring:
application:
name: api-gateway

cloud:
gateway:
# 全局配置
globalcors:
cors-configurations:
'[/**]':
allowedOrigins: "*"
allowedMethods:
- GET
- POST
- PUT
- DELETE
allowedHeaders: "*"
maxAge: 3600

      # 默认过滤器
      default-filters:
        - name: Retry
          args:
            retries: 3
            statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE
            methods: GET
            backoff:
              firstBackoff: 100ms
              maxBackoff: 500ms
              factor: 2
        - AddRequestHeader=X-Gateway-Service, ${spring.application.name}
      
      # 路由配置
      routes:
        # 订单服务
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**,/api/payment/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@ipKeyResolver}"
        
        # 账户服务
        - id: account-service
          uri: lb://account-service
          predicates:
            - Path=/api/account/**
          filters:
            - StripPrefix=1
        
        # 库存服务
        - id: storage-service
          uri: lb://storage-service
          predicates:
            - Path=/api/storage/**
          filters:
            - StripPrefix=1
        
        # 积分服务
        - id: points-service
          uri: lb://points-service
          predicates:
            - Path=/api/points/**
          filters:
            - StripPrefix=1
      
      # 熔断配置
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true

# Redis 配置（用于限流）
redis:
host: 192.168.1.201
port: 6379
password: your_password
database: 0
lettuce:
pool:
max-active: 50
max-idle: 20
min-idle: 5

# Nacos 配置
cloud:
nacos:
discovery:
server-addr: 192.168.1.10:8848,192.168.1.11:8848,192.168.1.12:8848
namespace: prod
group: DEFAULT_GROUP
config:
server-addr: 192.168.1.10:8848,192.168.1.11:8848,192.168.1.12:8848
file-extension: yaml
namespace: prod

# Sentinel 配置
sentinel:
transport:
dashboard: 192.168.1.50:8090
port: 8719
datasource:
ds1:
nacos:
server-addr: 192.168.1.10:8848
data-id: api-gateway-flow-rules
group-id: DEFAULT_GROUP
rule-type: flow
data-type: json

# Actuator 监控
management:
endpoints:
web:
exposure:
include: health,info,metrics,gateway
endpoint:
health:
show-details: always
metrics:
export:
prometheus:
enabled: true

# JVM 参数优化
logging:
level:
org.springframework.cloud.gateway: INFO
reactor.netty: INFO
