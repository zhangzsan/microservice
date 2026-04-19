package com.example.order.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Arrays;

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

    private static final String RESTORE_LUA_SCRIPT =
            "local idempotentKey = KEYS[1] " +
            "local stockKey = KEYS[2] " +
            "local quantity = tonumber(ARGV[1]) " +
            "if redis.call('EXISTS', idempotentKey) == 0 then " +
            "    redis.call('SETEX', idempotentKey, 86400, '1') " +
            "    local newStock = redis.call('INCRBY', stockKey, quantity) " +
            "    return newStock " +
            "else " +
            "    return -1 " +
            "end";

    private RedisScript<Long> deductScript;
    private RedisScript<Long> restoreScript;

    @PostConstruct
    public void init() {
        deductScript = new DefaultRedisScript<>(DEDUCT_LUA_SCRIPT, Long.class);
        restoreScript = new DefaultRedisScript<>(RESTORE_LUA_SCRIPT, Long.class);
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
            log.warn("Redis库存不足, 商品ID: {}, 请求数量: {}", productId, quantity);
            return false;
        }
    }

    public void restoreStockWithRedis(Long productId, Integer quantity, String orderNo) {
        String idempotentKey = STOCK_RESTORE_IDEMPOTENT_PREFIX + orderNo + ":" + productId;
        String stockKey = STOCK_KEY_PREFIX + productId;

        try {
            Long result = redisTemplate.execute(restoreScript, Arrays.asList(idempotentKey, stockKey), String.valueOf(quantity));
            if (result != -1) {
                log.info("Redis库存原子性恢复成功，商品ID: {}, 恢复后库存: {}", productId, result);
            } else {
                log.warn("Redis库存已恢复(幂等拦截), 商品ID: {}, 订单号: {}", productId, orderNo);
            }
        } catch (Exception e) {
            log.error("Redis库存恢复脚本执行异常, 商品ID: {}, 订单号: {}", productId, orderNo, e);
        }
    }
}