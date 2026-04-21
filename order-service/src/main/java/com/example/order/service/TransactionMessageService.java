package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.common.dto.OrderTimeoutMessage;
import com.example.common.exception.BusinessException;
import com.example.order.constant.MessageStatus;
import com.example.order.entity.TransactionMessage;
import com.example.order.mapper.TransactionMessageMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransactionMessageService {

    @Autowired
    private TransactionMessageMapper transactionMessageMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String PENDING_QUEUE_KEY = "tx_msg:pending";
    private static final String LOCK_KEY_PREFIX = "tx_msg:lock:";
    
    private final DefaultRedisScript<Long> acquireLockScript;
    private final DefaultRedisScript<Long> releaseLockScript;
    private final DefaultRedisScript<Long> addWithRetryScript;

    public TransactionMessageService() {
        acquireLockScript = new DefaultRedisScript<>();
        acquireLockScript.setScriptText(
            "local key = KEYS[1] " +
            "local ttl = tonumber(ARGV[1]) " +
            "if redis.call('EXISTS', key) == 0 then " +
            "    redis.call('SETEX', key, ttl, '1') " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end"
        );
        acquireLockScript.setResultType(Long.class);

        releaseLockScript = new DefaultRedisScript<>();
        releaseLockScript.setScriptText(
            "local key = KEYS[1] " +
            "local owner = ARGV[1] " +
            "if redis.call('GET', key) == owner then " +
            "    redis.call('DEL', key) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end"
        );
        releaseLockScript.setResultType(Long.class);

        addWithRetryScript = new DefaultRedisScript<>();
        addWithRetryScript.setScriptText(
            "local queueKey = KEYS[1] " +
            "local messageId = ARGV[1] " +
            "local score = tonumber(ARGV[2]) " +
            "redis.call('ZADD', queueKey, score, messageId) " +
            "return 1"
        );
        addWithRetryScript.setResultType(Long.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(TransactionMessage message) {
        transactionMessageMapper.insert(message);
        if (message.getStatus().equals(MessageStatus.PENDING.getValue())) {
            long score = message.getNextRetryTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(PENDING_QUEUE_KEY, message.getId().toString(), score);
            log.debug("消息已加入Redis调度队列, ID: {}, 时间戳: {}", message.getId(), score);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void sendPendingMessages() {
        long now = System.currentTimeMillis();
        
        Set<String> messageIds = stringRedisTemplate.opsForZSet().rangeByScore(PENDING_QUEUE_KEY, 0, now, 0, 20);
        
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        log.info("从Redis获取到 {} 条待发送消息", messageIds.size());
        for (String messageId : messageIds) {
            String lockKey = LOCK_KEY_PREFIX + messageId;
            String lockOwner = Thread.currentThread().getId() + ":" + System.currentTimeMillis();
            //设置超时时间30s
            Long acquired = stringRedisTemplate.execute(acquireLockScript, List.of(lockKey), "30");
            
            if (acquired == 0) {
                log.debug("消息正在处理中,跳过, ID: {}", messageId);
                continue;
            }
            try {
                Long id = Long.parseLong(messageId);
                TransactionMessage message = transactionMessageMapper.selectById(id);
                
                if (message == null) {
                    log.warn("消息不存在, 从Redis清理，ID: {}", messageId);
                    cleanupMessage(messageId);
                    continue;
                }

                if (isMessageCompleted(message.getStatus())) {
                    log.debug("消息已完成, 从Redis清理，ID: {}", messageId);
                    cleanupMessage(messageId);
                    continue;
                }

                sendMessage(message);
                
                cleanupMessage(messageId);
                
            } catch (Exception e) {
                log.error("发送消息失败，messageId: {}", messageId, e);
                handleSendFailureWithRedis(messageId, e.getMessage(), lockOwner);
            } finally {
                releaseLock(lockKey, lockOwner);
            }
        }
    }

    private void releaseLock(String lockKey, String lockOwner) {
        try {
            stringRedisTemplate.execute(releaseLockScript, Collections.singletonList(lockKey), lockOwner);
        } catch (Exception e) {
            log.error("释放锁失败, lockKey: {}", lockKey, e);
        }
    }

    private void sendMessage(TransactionMessage message) {
        String destination = message.getTopic() + ":" + message.getTag();
        
        if ("points-tx-topic".equals(message.getTopic())) {
            try {
                Object body = objectMapper.readValue(message.getMessageBody(), Object.class);
                
                rocketMQTemplate.sendMessageInTransaction(message.getTopic(), MessageBuilder.withPayload(body).build(), extractOrderNo(message.getMessageBody()));
                
                updateMessageStatus(message.getId());
                log.info("事务消息发送成功, messageId: {}, topic: {}", message.getMessageId(), message.getTopic());
            } catch (Exception e) {
                log.error("发送事务消息失败，messageId: {}", message.getMessageId(), e);
                throw new BusinessException("发送事务消息失败:" + e.getMessage());
            }
        } else if ("order-timeout-topic".equals(message.getTopic())) {
            // 超时消息: 计算剩余延迟时间,保证超时准确性
            sendTimeoutMessageWithAccurateDelay(message);
        } else {
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(message.getMessageBody()).build());
            updateMessageStatus(message.getId());
            log.info("普通消息发送成功, messageId: {}, topic: {}", message.getMessageId(), message.getTopic());
        }
    }

    /**
     * 发送超时消息,并计算准确的延迟时间
     * 确保从订单创建时刻开始计算30分钟,而不是从重发时刻开始
     */
    private void sendTimeoutMessageWithAccurateDelay(TransactionMessage message) {
        try {
            OrderTimeoutMessage timeoutMsg =
                objectMapper.readValue(message.getMessageBody(), OrderTimeoutMessage.class);
            
            // 计算从订单创建到现在的时长
            LocalDateTime orderCreateTime = message.getCreatedTime();
            Duration elapsed = Duration.between(orderCreateTime, LocalDateTime.now());
            
            long expectedDelayMinutes = 30; // 预期超时时间30分钟
            long elapsedMinutes = elapsed.toMinutes();
            
            if (elapsedMinutes >= expectedDelayMinutes) {
                // 已经超过30分钟,立即发送(不延迟)
                log.info("订单已超时,立即发送超时消息, orderNo: {}, 已过去: {}分钟", timeoutMsg.getOrderNo(), elapsedMinutes);
                
                rocketMQTemplate.syncSend("order-timeout-topic", MessageBuilder.withPayload(timeoutMsg).build());
            } else {
                // 计算剩余延迟时间
                long remainingMinutes = expectedDelayMinutes - elapsedMinutes;
                log.info("订单未超时,发送延迟消息, orderNo: {}, 剩余: {}分钟", timeoutMsg.getOrderNo(), remainingMinutes);
                
                // RocketMQ延迟级别: 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
                int delayLevel = calculateDelayLevel(remainingMinutes);
                
                rocketMQTemplate.syncSend("order-timeout-topic", MessageBuilder.withPayload(timeoutMsg).build(), 3000, delayLevel);
            }
            
            updateMessageStatus(message.getId());
            log.info("超时消息发送成功, messageId: {}, orderNo: {}", message.getMessageId(), timeoutMsg.getOrderNo());
            
        } catch (Exception e) {
            log.error("发送超时消息失败，messageId: {}", message.getMessageId(), e);
            throw new BusinessException("发送超时消息失败:" + e.getMessage());
        }
    }

    /**
     * 根据剩余分钟数计算RocketMQ延迟级别
     * RocketMQ延迟级别: 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    private int calculateDelayLevel(long minutes) {
        if (minutes <= 0) return 1;           // 立即发送
        if (minutes <= 1) return 5;           // 1分钟
        if (minutes <= 2) return 6;           // 2分钟
        if (minutes <= 3) return 7;           // 3分钟
        if (minutes <= 4) return 8;           // 4分钟
        if (minutes <= 5) return 9;           // 5分钟
        if (minutes <= 6) return 10;          // 6分钟
        if (minutes <= 7) return 11;          // 7分钟
        if (minutes <= 8) return 12;          // 8分钟
        if (minutes <= 9) return 13;          // 9分钟
        if (minutes <= 10) return 14;         // 10分钟
        if (minutes <= 20) return 15;         // 20分钟
        return 16;                             // 30分钟
    }

    private String extractOrderNo(String messageBody) {
        try {
            JsonNode node = objectMapper.readTree(messageBody);
            return node.has("orderNo") ? node.get("orderNo").asText() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void updateMessageStatus(Long id) {
        LambdaUpdateWrapper<TransactionMessage> updateWrapper = 
            new LambdaUpdateWrapper<TransactionMessage>().eq(TransactionMessage::getId, id)
                    .eq(TransactionMessage::getStatus, MessageStatus.PENDING.getValue())
                .set(TransactionMessage::getStatus, MessageStatus.SENT.getValue());
        
        transactionMessageMapper.update(null, updateWrapper);
    }

    private void handleSendFailureWithRedis(String messageId, String errorMsg, String lockOwner) {
        try {
            Long id = Long.parseLong(messageId);
            TransactionMessage message = transactionMessageMapper.selectById(id);
            
            if (message == null) {
                log.warn("消息不存在, 从Redis清理, ID: {}", messageId);
                cleanupMessage(messageId);
                return;
            }

            // 使用CAS原子更新重试次数，避免高并发问题
            boolean casSuccess = false;
            int maxCasRetries = 3;  // CAS最多重试3次
            
            for (int i = 0; i < maxCasRetries && !casSuccess; i++) {
                // 重新查询最新数据
                TransactionMessage latestMessage = transactionMessageMapper.selectById(id);
                if (latestMessage == null) {
                    log.warn("消息不存在, 从Redis清理, ID: {}", messageId);
                    cleanupMessage(messageId);
                    return;
                }
                
                int currentRetryCount = latestMessage.getRetryCount();
                int newRetryCount = currentRetryCount + 1;
                LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(newRetryCount * 2L);
                
                LambdaUpdateWrapper<TransactionMessage> updateWrapper = 
                    new LambdaUpdateWrapper<TransactionMessage>()
                        .eq(TransactionMessage::getId, id)
                        .eq(TransactionMessage::getRetryCount, currentRetryCount)  // CAS条件
                        .set(TransactionMessage::getRetryCount, newRetryCount)
                        .set(TransactionMessage::getNextRetryTime, nextRetryTime)
                        .set(TransactionMessage::getErrorMessage, errorMsg);
                
                if (newRetryCount >= currentRetryCount) {
                    updateWrapper.set(TransactionMessage::getStatus, MessageStatus.FAILED.getValue());
                    cleanupMessage(messageId);
                    log.error("消息发送失败已达最大重试次数, 需人工介入，messageId: {}, topic: {}",
                        latestMessage.getMessageId(), latestMessage.getTopic());
                    transactionMessageMapper.update(null, updateWrapper);
                    casSuccess = true;
                } else {
                    int updated = transactionMessageMapper.update(null, updateWrapper);
                    if (updated > 0) {
                        casSuccess = true;
                        long nextRetryScore = nextRetryTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        
                        stringRedisTemplate.execute(addWithRetryScript, List.of(PENDING_QUEUE_KEY), messageId, String.valueOf(nextRetryScore));
                        
                        log.warn("消息发送失败, 将重试, messageId: {}, 重试次数: {}/{}", latestMessage.getMessageId(), newRetryCount, latestMessage.getMaxRetryCount());
                    } else {
                        log.warn("CAS更新失败，准备重试({}/{}), ID: {}", i + 1, maxCasRetries, id);
                        if (i < maxCasRetries - 1) {
                            try {
                                Thread.sleep(50);  // 短暂等待后重试
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            }
            
            if (!casSuccess) {
                log.error("CAS更新多次失败，放弃更新，ID: {}, messageId: {}", id, messageId);
            }
        } catch (Exception e) {
            log.error("处理发送失败异常，messageId: {}", messageId, e);
        }
    }

    private void cleanupMessage(String messageId) {
        stringRedisTemplate.opsForZSet().remove(PENDING_QUEUE_KEY, messageId);
        log.debug("清理Redis中的消息: {}", messageId);
    }

    private boolean isMessageCompleted(Integer status) {
        return status.equals(MessageStatus.SENT.getValue()) ||
               status.equals(MessageStatus.CONFIRMED.getValue()) ||
               status.equals(MessageStatus.FAILED.getValue());
    }

    /**
     * 合并维护任务: Redis对账 + 恢复卡住的锁
     * 每10分钟执行一次,减少定时任务数量
     */
    @Scheduled(fixedDelay = 600000)
    public void maintenanceTasks() {
        log.info("开始执行消息系统维护任务");
        
        // 1. Redis与数据库对账
        reconcileRedisWithDatabase();
        
        // 2. 恢复卡住的消息锁
        recoverStuckMessages();
        
        log.info("消息系统维护任务完成");
    }

    /**
     * Redis与数据库对账,清理已完成的消息
     */
    private void reconcileRedisWithDatabase() {
        log.info("开始对账Redis与数据库状态");
        
        Set<String> pendingIds = stringRedisTemplate.opsForZSet().range(PENDING_QUEUE_KEY, 0, -1);
        
        if (pendingIds == null || pendingIds.isEmpty()) {
            log.info("Redis中无待发送消息");
            return;
        }

        List<Long> ids = pendingIds.stream().map(Long::parseLong).collect(Collectors.toList());

        List<TransactionMessage> messages = transactionMessageMapper.selectBatchIds(ids);
        
        int cleanedCount = 0;
        for (TransactionMessage message : messages) {
            if (isMessageCompleted(message.getStatus())) {
                stringRedisTemplate.opsForZSet().remove(PENDING_QUEUE_KEY, message.getId().toString());
                cleanedCount++;
                log.debug("清理已完成消息: ID={}", message.getId());
            }
        }
        
        log.info("对账完成, 清理 {} 条已完成消息", cleanedCount);
    }

    /**
     * 恢复卡住的消息锁(锁存在但没有TTL,说明释放失败)
     */
    private void recoverStuckMessages() {
        log.info("检查卡住的消息");
        Set<String> pendingIds = stringRedisTemplate.opsForZSet().range(PENDING_QUEUE_KEY, 0, -1);
        
        if (pendingIds == null || pendingIds.isEmpty()) {
            return;
        }

        int recoveredCount = 0;
        for (String messageId : pendingIds) {
            String lockKey = LOCK_KEY_PREFIX + messageId;
            Boolean exists = stringRedisTemplate.hasKey(lockKey);
            
            if (exists) {
                Long ttl = stringRedisTemplate.getExpire(lockKey);
                if (ttl < 0) {
                    stringRedisTemplate.delete(lockKey);
                    recoveredCount++;
                    log.warn("恢复卡住的消息锁: {}", messageId);
                }
            }
        }
        
        if (recoveredCount > 0) {
            log.info("恢复了 {} 个卡住的消息锁", recoveredCount);
        }
    }
}
