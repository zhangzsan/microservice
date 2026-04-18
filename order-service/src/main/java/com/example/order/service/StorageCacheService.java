package com.example.order.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StorageCacheService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    private static final String STOCK_LOCK_PREFIX = "lock:stock:product:";

    private static final String STOCK_RESTORE_IDEMPOTENT_PREFIX = "stock:restore:idempotent:";

    private static final String DEDUCT_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local quantity = tonumber(ARGV[1]) " +
            "local current = tonumber(redis.call('get', key) or '0') " +
            "if current >= quantity then " +
            "    local newStock = current - quantity " +
            "    redis.call('set', key, tostring(newStock)) " +
            "    return newStock " +
            "else " +
            "    return -1 " +
            "end";

    private RedisScript<Long> deductScript;

    @PostConstruct
    public void init() {
        deductScript = new DefaultRedisScript<>(DEDUCT_LUA_SCRIPT, Long.class);
    }


    private Long deductStockAtomic(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        return redisTemplate.execute(deductScript, Collections.singletonList(key), String.valueOf(quantity));
    }


    public boolean deductStockWithRedis(Long productId, Integer quantity) {
        log.info("开始扣减Redis库存，商品ID: {}, 扣减数量: {}", productId, quantity);
        Long remain = deductStockAtomic(productId, quantity);
        if (remain >= 0) {
            log.info("Redis库存预扣成功，商品ID: {}, 剩余库存: {}", productId, remain);
            return true;
        } else {
            log.warn("Redis库存不足，商品ID: {}, 请求数量: {}", productId, quantity);
            return false;
        }
    }

    public void restoreStockWithRedis(Long productId, Integer quantity, String orderNo) {
        String idempotentKey = STOCK_RESTORE_IDEMPOTENT_PREFIX + orderNo + ":" + productId;
        
        Boolean isFirst = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 24, TimeUnit.HOURS);
        
        if (Boolean.FALSE.equals(isFirst)) {
            log.warn("Redis库存已恢复, 幂等返回，商品ID: {}", productId);
            return;
        }
        
        try {
            String key = STOCK_KEY_PREFIX + productId;
            Long newStock = redisTemplate.opsForValue().increment(key, quantity);
            log.info("Redis库存恢复成功，商品ID: {}, 恢复后库存: {}", productId, newStock);
        } catch (Exception e) {
            redisTemplate.delete(idempotentKey);
            log.error("Redis库存恢复失败, 商品ID: {}", productId, e);
            throw e;
        }
    }

    public void initStockToRedis(Long productId, Integer stock) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(stock));
        log.info("初始化Redis库存，商品ID: {}, 库存: {}", productId, stock);
    }

    public boolean deductStockWithLock(Long productId, Integer quantity) {
        String lockKey = STOCK_LOCK_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                String key = STOCK_KEY_PREFIX + productId;
                String stockStr = redisTemplate.opsForValue().get(key);
                if (stockStr == null) return false;
                int stock = Integer.parseInt(stockStr);
                if (stock >= quantity) {
                    redisTemplate.opsForValue().set(key, String.valueOf(stock - quantity));
                    log.info("分布式锁扣减库存成功，商品ID: {}, 剩余: {}", productId, stock - quantity);
                    return true;
                } else {
                    log.warn("库存不足，商品ID: {}, 当前库存: {}, 请求数量: {}", productId, stock, quantity);
                    return false;
                }
            } else {
                log.warn("获取分布式锁失败，商品ID: {}", productId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("分布式锁等待被中断，商品ID: {}", productId, e);
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}