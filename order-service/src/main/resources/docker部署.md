version: '3.8'

services:
  # Nacos 集群
  nacos1:
    image: nacos/nacos-server:v2.2.3
    container_name: nacos1
    environment:
      - MODE=cluster
      - NACOS_SERVERS=nacos1:8848 nacos2:8848 nacos3:8848
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    ports:
      - "8848:8848"
      - "9848:9848"
    networks:
      - microservice-net

  nacos2:
    image: nacos/nacos-server:v2.2.3
    container_name: nacos2
    environment:
      - MODE=cluster
      - NACOS_SERVERS=nacos1:8848 nacos2:8848 nacos3:8848
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    ports:
      - "8849:8848"
    networks:
      - microservice-net

  nacos3:
    image: nacos/nacos-server:v2.2.3
    container_name: nacos3
    environment:
      - MODE=cluster
      - NACOS_SERVERS=nacos1:8848 nacos2:8848 nacos3:8848
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=root
    ports:
      - "8850:8848"
    networks:
      - microservice-net

  # MySQL Master-Slave
  mysql-master:
    image: mysql:8.0
    container_name: mysql-master
    environment:
      - MYSQL_ROOT_PASSWORD=root
    ports:
      - "3306:3306"
    volumes:
      - ./mysql/master/conf:/etc/mysql/conf.d
      - ./mysql/master/data:/var/lib/mysql
    networks:
      - microservice-net

  mysql-slave:
    image: mysql:8.0
    container_name: mysql-slave
    environment:
      - MYSQL_ROOT_PASSWORD=root
    ports:
      - "3307:3306"
    volumes:
      - ./mysql/slave/conf:/etc/mysql/conf.d
      - ./mysql/slave/data:/var/lib/mysql
    depends_on:
      - mysql-master
    networks:
      - microservice-net

  # Redis Sentinel
  redis-master:
    image: redis:7.0
    container_name: redis-master
    ports:
      - "6379:6379"
    volumes:
      - ./redis/master/redis.conf:/usr/local/etc/redis/redis.conf
    command: redis-server /usr/local/etc/redis/redis.conf
    networks:
      - microservice-net

  redis-sentinel1:
    image: redis:7.0
    container_name: redis-sentinel1
    ports:
      - "26379:26379"
    volumes:
      - ./redis/sentinel1/sentinel.conf:/usr/local/etc/redis/sentinel.conf
    command: redis-sentinel /usr/local/etc/redis/sentinel.conf
    depends_on:
      - redis-master
    networks:
      - microservice-net

  # RocketMQ
  namesrv:
    image: apache/rocketmq:5.1.4
    container_name: rmqnamesrv
    ports:
      - "9876:9876"
    command: sh mqnamesrv
    networks:
      - microservice-net

  broker:
    image: apache/rocketmq:5.1.4
    container_name: rmqbroker
    ports:
      - "10909:10909"
      - "10911:10911"
      - "10912:10912"
    environment:
      - NAMESRV_ADDR=namesrv:9876
    command: sh mqbroker -c /home/rocketmq/rocketmq-5.1.4/conf/broker.conf
    depends_on:
      - namesrv
    volumes:
      - ./rocketmq/broker.conf:/home/rocketmq/rocketmq-5.1.4/conf/broker.conf
    networks:
      - microservice-net

  # Gateway 集群
  gateway1:
    build: ./gateway
    container_name: gateway1
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - NACOS_SERVER_ADDR=192.168.1.10:8848
    depends_on:
      - nacos1
    networks:
      - microservice-net
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '1.0'

  gateway2:
    build: ./gateway
    container_name: gateway2
    ports:
      - "8081:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - NACOS_SERVER_ADDR=192.168.1.10:8848
    depends_on:
      - nacos1
    networks:
      - microservice-net

  # Order Service 集群
  order-service1:
    build: ./order-service
    container_name: order-service1
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8086
    depends_on:
      - nacos1
      - mysql-master
      - redis-master
    networks:
      - microservice-net

  order-service2:
    build: ./order-service
    container_name: order-service2
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8086
    depends_on:
      - nacos1
      - mysql-master
      - redis-master
    networks:
      - microservice-net

networks:
  microservice-net:
    driver: bridge
