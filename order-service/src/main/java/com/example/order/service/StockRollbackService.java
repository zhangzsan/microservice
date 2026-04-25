package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.common.constant.OperationStatus;
import com.example.common.constant.OperationType;
import com.example.common.dto.OrderTimeoutMessage;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.exception.BusinessException;
import com.example.order.entity.OrderOperationLog;
import com.example.order.feign.StorageFeignClient;
import com.example.order.mapper.OrderOperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优化 ResourceRollbackService - 异步批量处理
 * 恢复库存的操作
 */
@Service
@Slf4j
public class StockRollbackService {

    @Autowired
    private OrderOperationLogMapper operationLogMapper;

    @Autowired
    private StorageFeignClient storageFeignClient;

    @Autowired
    private StorageCacheService storageCacheService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 异步创建回滚任务
     * 使用@Async避免阻塞MQ消费者线程,提升吞吐量
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void createRollbackTask(OrderTimeoutMessage message) {
        createRollbackTaskInternal(message);
    }

    /**
     * 同步创建回滚任务
     * 用于补偿场景,确保任务一定被创建
     */
    @Transactional(rollbackFor = Exception.class)
    public void createRollbackTaskSync(OrderTimeoutMessage message) {
        createRollbackTaskInternal(message);
    }

    /**
     * 内部方法: 创建回滚任务的核心逻辑
     */
    private void createRollbackTaskInternal(OrderTimeoutMessage message) {
        try {
            OrderOperationLog existLog = operationLogMapper.selectOne(new LambdaQueryWrapper<OrderOperationLog>()
                    .eq(OrderOperationLog::getOrderNo, message.getOrderNo()).eq(OrderOperationLog::getOperationType, OperationType.TIMEOUT_CANCEL.getValue()));

            if (existLog != null) {
                if (existLog.getOperationStatus() == OperationStatus.SUCCESS.getValue()) {
                    log.warn("回滚任务已成功, 无需重复创建, 订单号: {}", message.getOrderNo());
                } else {
                    log.warn("回滚任务已存在且未完成, 订单号: {}, 状态: {}", message.getOrderNo(), existLog.getOperationStatus());
                }
                return;
            }

            OrderOperationLog blog = new OrderOperationLog();
            blog.setOrderNo(message.getOrderNo());
            blog.setOperationType(OperationType.TIMEOUT_CANCEL.getValue());
            blog.setOperationStatus(OperationStatus.PROCESSING.getValue());
            blog.setRetryCount(0);
            blog.setMaxRetryCount(3);
            blog.setRequestData(objectMapper.writeValueAsString(message));
            blog.setNextRetryTime(LocalDateTime.now());
            operationLogMapper.insert(blog);
            log.info("创建回滚任务成功，订单号: {}", message.getOrderNo());
            
        } catch (DuplicateKeyException e) {
            log.warn("回滚任务已存在(唯一索引冲突), 订单号: {}", message.getOrderNo());
        } catch (Exception e) {
            log.error("创建回滚任务失败, 订单号: {}", message.getOrderNo(), e);
            throw new BusinessException("创建回滚任务失败:" +  e);
        }
    }

    /**
     * 异步执行回滚任务（按订单号）
     * 用于OrderTimeoutConsumer调用，根据订单号查找任务并执行
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void executeRollbackAsync(String orderNo) {
        OrderOperationLog operationLog = operationLogMapper.selectOne(
                new LambdaQueryWrapper<OrderOperationLog>()
                        .eq(OrderOperationLog::getOrderNo, orderNo)
                        .eq(OrderOperationLog::getOperationType, OperationType.TIMEOUT_CANCEL.getValue())
        );

        if (operationLog == null) {
            log.error("回滚任务记录不存在, 订单号: {}", orderNo);
            return;
        }

        executeRollback(operationLog.getId());
    }
    /**
     * 异步执行回滚任务
     * 使用@Async避免阻塞调用方,提升并发处理能力
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void executeRollback(Long logId) {
        OrderOperationLog operationLog = operationLogMapper.selectById(logId);
        if (operationLog == null) {
            log.error("操作日志不存在, ID: {}", logId);
            return;
        }

        if (operationLog.getOperationStatus() == OperationStatus.SUCCESS.getValue()) {
            log.info("任务已成功, 无需重复执行，ID: {}, 订单号: {}", logId, operationLog.getOrderNo());
            return;
        }

        if (operationLog.getRetryCount() >= operationLog.getMaxRetryCount()) {
            log.error("重试次数已达上限, 需要人工介入, ID: {}, 订单号: {}", logId, operationLog.getOrderNo());
            updateLogStatus(logId, OperationStatus.FAILED, "重试次数已达上限");
            return;
        }

        try {
            OrderTimeoutMessage message = objectMapper.readValue(operationLog.getRequestData(), OrderTimeoutMessage.class);
            rollbackStorage(message);
            rollbackRedisStock(message);
            updateLogStatus(logId, OperationStatus.SUCCESS, null);
            log.info("资源回滚成功, 订单号: {}", message.getOrderNo());
        } catch (Exception e) {
            // 使用CAS原子更新重试次数，避免高并发问题
            int currentRetryCount = operationLog.getRetryCount();
            int newRetryCount = currentRetryCount + 1;
            LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(newRetryCount * 5L);

            LambdaUpdateWrapper<OrderOperationLog> updateWrapper = 
                new LambdaUpdateWrapper<OrderOperationLog>()
                    .eq(OrderOperationLog::getId, logId)
                    .eq(OrderOperationLog::getRetryCount, currentRetryCount)  // CAS条件：确保基于最新版本更新
                    .set(OrderOperationLog::getRetryCount, newRetryCount)
                    .set(OrderOperationLog::getNextRetryTime, nextRetryTime)
                    .set(OrderOperationLog::getErrorMessage, e.getMessage())
                    .set(OrderOperationLog::getOperationStatus, OperationStatus.FAILED.getValue());

            int updated = operationLogMapper.update(null, updateWrapper);
            if (updated == 0) {
                log.warn("CAS更新失败(可能已被其他线程更新)，跳过本次重试计数，ID: {}, 订单号: {}", logId, operationLog.getOrderNo());
                return;
            }
            
            log.error("资源回滚失败，将重试，订单号: {}, 重试次数: {}/{}", operationLog.getOrderNo(), newRetryCount, operationLog.getMaxRetryCount(), e);
        }
    }

    public void executeBatchRollback(List<Long> logIds) {
        log.info("开始批量执行回滚任务，数量: {}", logIds.size());
        for (Long logId : logIds) {
            try {
                executeRollback(logId);
            } catch (Exception e) {
                log.error("批量回滚中单个任务失败,ID: {}", logId, e);
            }
        }
        log.info("批量回滚任务完成，数量: {}", logIds.size());
    }

    private void rollbackStorage(OrderTimeoutMessage message) {
        StorageRestoreRequest request = new StorageRestoreRequest();
        request.setProductId(message.getProductId());
        request.setQuantity(message.getQuantity());
        request.setOrderNo(message.getOrderNo());
        try {
            storageFeignClient.restore(request);
            log.info("库存回滚成功，订单号: {}", message.getOrderNo());
        } catch (Exception e) {
            log.error("库存回滚失败，订单号: {}", message.getOrderNo(), e);
            throw e;
        }
    }

    private void rollbackRedisStock(OrderTimeoutMessage message) {
        try {
            storageCacheService.restoreStockWithRedis(message.getProductId(), message.getQuantity(),message.getOrderNo());
            log.info("Redis库存回滚成功，订单号: {}", message.getOrderNo());
        } catch (Exception e) {
            log.error("Redis库存回滚失败，订单号: {}", message.getOrderNo(), e);
            throw e;
        }
    }

    private void updateLogStatus(Long logId, OperationStatus status, String errorMessage) {
        LambdaUpdateWrapper<OrderOperationLog> updateWrapper = 
            new LambdaUpdateWrapper<OrderOperationLog>()
                .eq(OrderOperationLog::getId, logId).set(OrderOperationLog::getOperationStatus, status.getValue());

        if (errorMessage != null) {
            updateWrapper.set(OrderOperationLog::getErrorMessage, errorMessage);
        }

        operationLogMapper.update(null, updateWrapper);
    }
}
