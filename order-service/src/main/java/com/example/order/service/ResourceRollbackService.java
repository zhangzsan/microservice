
package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.common.constant.OperationStatus;
import com.example.common.constant.OperationType;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.dto.OrderTimeoutMessage;
import com.example.common.dto.StorageRestoreRequest;
import com.example.order.entity.OrderOperationLog;
import com.example.order.feign.AccountFeignClient;
import com.example.order.feign.StorageFeignClient;
import com.example.order.mapper.OrderOperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class ResourceRollbackService {

    @Autowired
    private OrderOperationLogMapper operationLogMapper;

    @Autowired
    private StorageFeignClient storageFeignClient;

    @Autowired
    private AccountFeignClient accountFeignClient;

    @Autowired
    private StorageCacheService storageCacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createRollbackTask(OrderTimeoutMessage message) {
        try {
            OrderOperationLog existLog = operationLogMapper.selectOne(
                new LambdaQueryWrapper<OrderOperationLog>()
                    .eq(OrderOperationLog::getOrderNo, message.getOrderNo())
                    .eq(OrderOperationLog::getOperationType, OperationType.TIMEOUT_CANCEL.getValue()));

            if (existLog != null) {
                log.warn("回滚任务已存在，订单号: {}", message.getOrderNo());
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
        } catch (Exception e) {
            log.error("创建回滚任务失败，订单号: {}", message.getOrderNo(), e);
            throw new RuntimeException("创建回滚任务失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeRollback(Long logId) {
        OrderOperationLog operationLog = operationLogMapper.selectById(logId);
        if (operationLog == null) {
            log.error("操作日志不存在，ID: {}", logId);
            return;
        }

        if (operationLog.getOperationStatus() == OperationStatus.SUCCESS.getValue()) {
            log.info("任务已成功，无需重复执行，ID: {}", logId);
            return;
        }

        if (operationLog.getRetryCount() >= operationLog.getMaxRetryCount()) {
            log.error("重试次数已达上限，需要人工介入，ID: {}", logId);
            updateLogStatus(logId, OperationStatus.FAILED, "重试次数已达上限");
            return;
        }

        try {
            OrderTimeoutMessage message = objectMapper.readValue(
                operationLog.getRequestData(), OrderTimeoutMessage.class);

            rollbackStorage(message);
            rollbackAccount(message);
            rollbackRedisStock(message);

            updateLogStatus(logId, OperationStatus.SUCCESS, null);
            log.info("资源回滚成功，订单号: {}", message.getOrderNo());

        } catch (Exception e) {
            int retryCount = operationLog.getRetryCount() + 1;
            LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(retryCount * 5L);

            LambdaUpdateWrapper<OrderOperationLog> updateWrapper = 
                new LambdaUpdateWrapper<OrderOperationLog>()
                    .eq(OrderOperationLog::getId, logId)
                    .set(OrderOperationLog::getRetryCount, retryCount)
                    .set(OrderOperationLog::getNextRetryTime, nextRetryTime)
                    .set(OrderOperationLog::getErrorMessage, e.getMessage())
                    .set(OrderOperationLog::getOperationStatus, OperationStatus.FAILED.getValue());

            operationLogMapper.update(null, updateWrapper);

            log.error("资源回滚失败，将重试，订单号: {}, 重试次数: {}", 
                operationLog.getOrderNo(), retryCount, e);
        }
    }

    private void rollbackStorage(OrderTimeoutMessage message) {
        StorageRestoreRequest request = new StorageRestoreRequest();
        request.setProductId(message.getProductId());
        request.setQuantity(message.getQuantity());
        request.setOrderNo(message.getOrderNo());
        storageFeignClient.restore(request);
        log.info("库存回滚成功，订单号: {}", message.getOrderNo());
    }

    private void rollbackAccount(OrderTimeoutMessage message) {
        AccountRestoreRequest request = new AccountRestoreRequest();
        request.setUserId(message.getUserId());
        request.setAmount(message.getAmount());
        request.setOrderNo(message.getOrderNo());
        accountFeignClient.restore(request);
        log.info("余额回滚成功，订单号: {}", message.getOrderNo());
    }

    private void rollbackRedisStock(OrderTimeoutMessage message) {
        storageCacheService.restoreStockWithRedis(message.getProductId(), message.getQuantity());
        log.info("Redis库存回滚成功，订单号: {}", message.getOrderNo());
    }

    private void updateLogStatus(Long logId, OperationStatus status, String errorMessage) {
        LambdaUpdateWrapper<OrderOperationLog> updateWrapper = 
            new LambdaUpdateWrapper<OrderOperationLog>()
                .eq(OrderOperationLog::getId, logId)
                .set(OrderOperationLog::getOperationStatus, status.getValue());

        if (errorMessage != null) {
            updateWrapper.set(OrderOperationLog::getErrorMessage, errorMessage);
        }

        operationLogMapper.update(null, updateWrapper);
    }
}
