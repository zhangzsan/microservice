package com.example.storage;

import com.example.storage.entity.Storage;
import com.example.storage.mapper.StorageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目启动时: 把数据库库存初始化到 Redis
 */
@Component
public class StockInitRunner implements CommandLineRunner {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StorageMapper storageMapper;

    // 项目启动后自动执行
    @Override
    public void run(String... args) {
        System.out.println("===== 开始初始化商品库存到 Redis =====");
        // 1. 从数据库查询所有库存
        List<Storage> storages = storageMapper.selectList(null);
        // 2. 转成 key-value 结构
        Map<String, Integer> stockMap = storages.stream()
                .collect(Collectors.toMap(
                        stock -> "stock:product:" + stock.getProductId(), stock -> stock.getTotal() - stock.getUsed() // 库存数量
                ));
        // 3.批量存入Redis
        redisTemplate.opsForValue().multiSet(stockMap);
        System.out.println("===== 库存初始化完成，共加载：" + storages.size() + " 条 =====");
    }
}