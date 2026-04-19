package com.example.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.constant.OperationStatus;
import com.example.common.constant.OrderStatus;
import com.example.common.dto.OrderTimeoutMessage;
import com.example.order.entity.Order;
import com.example.order.entity.OrderOperationLog;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.OrderOperationLogMapper;
import com.example.order.service.StockRollbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 回滚任务执行定时任务
 * 定期扫描待执行的回滚任务并异步执行
 */
@Component
@Slf4j
public class RollbackTaskExecutor {

    @Autowired
    private OrderOperationLogMapper operationLogMapper;

    @Autowired
    private StockRollbackService stockRollbackService;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 每30秒执行一次,扫描待处理的回滚任务
     */
    @Scheduled(fixedDelay = 30000)
    public void executePendingRollbackTasks() {
        log.debug("开始扫描待执行的回滚任务");
        
        // 查询状态为PROCESSING且到达重试时间的任务
        List<OrderOperationLog> pendingTasks = operationLogMapper.selectList(
            new LambdaQueryWrapper<OrderOperationLog>()
                .eq(OrderOperationLog::getOperationStatus, OperationStatus.PROCESSING.getValue())
                .le(OrderOperationLog::getNextRetryTime, LocalDateTime.now())
                .last("LIMIT 50")  // 每次最多处理50个任务
        );

        if (pendingTasks.isEmpty()) {
            log.debug("无待执行的回滚任务");
            return;
        }

        log.info("发现 {} 个待执行的回滚任务,开始批量处理", pendingTasks.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (OrderOperationLog task : pendingTasks) {
            try {
                // 异步执行回滚(StockRollbackService.executeRollback已有@Async)
                stockRollbackService.executeRollback(task.getId());
                successCount++;
            } catch (Exception e) {
                log.error("提交回滚任务失败, ID: {}, 订单号: {}", task.getId(), task.getOrderNo(), e);
                failCount++;
            }
        }
        
        log.info("回滚任务提交完成 - 成功: {}, 失败: {}", successCount, failCount);
    }

    /**
     * 每分钟扫描一次,检测超时订单是否有对应的回滚任务
     * 补偿机制: 如果createRollbackTask失败,这里会兜底创建
     */
    @Scheduled(fixedDelay = 60000)
    public void compensateMissingRollbackTasks() {
        log.debug("开始扫描缺失的回滚任务");
        
        // 查询10分钟前超时的订单(给消息消费留一些时间)
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(10);
        
        List<Order> timeoutOrders = orderMapper.selectList(
            new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.TIMEOUT.getValue())
                .lt(Order::getUpdatedTime, timeoutThreshold)
                .last("LIMIT 100")  // 每次最多处理100个订单
        );

        if (timeoutOrders.isEmpty()) {
            log.debug("无需补偿的超时订单");
            return;
        }

        log.info("发现 {} 个可能需要补偿的超时订单", timeoutOrders.size());
        
        int createdCount = 0;
        int skippedCount = 0;
        
        for (Order order : timeoutOrders) {
            try {
                // 检查是否已存在回滚任务
                Long existCount = operationLogMapper.selectCount(
                    new LambdaQueryWrapper<OrderOperationLog>().eq(OrderOperationLog::getOrderNo, order.getOrderNo()));
                
                if (existCount > 0) {
                    skippedCount++;
                    continue;
                }
                
                // 不存在回滚任务,补偿创建
                log.warn("检测到缺失的回滚任务,补偿创建 - 订单号: {}", order.getOrderNo());
                
                OrderTimeoutMessage message = new OrderTimeoutMessage();
                message.setOrderNo(order.getOrderNo());
                message.setProductId(order.getProductId());
                message.setQuantity(order.getQuantity());

                // 同步创建回滚任务(确保成功)
                stockRollbackService.createRollbackTaskSync(message);
                createdCount++;
                
                log.info("补偿创建回滚任务成功 - 订单号: {}", order.getOrderNo());
                
            } catch (Exception e) {
                log.error("补偿创建回滚任务失败 - 订单号: {}", order.getOrderNo(), e);
            }
        }
        
        if (createdCount > 0 || skippedCount > 0) {
            log.info("回滚任务补偿完成 - 创建: {}, 跳过(已存在): {}", createdCount, skippedCount);
        }
    }

    /**
     * 每小时清理一次已完成的任务日志(可选,避免数据堆积)
     */
    @Scheduled(fixedDelay = 3600000)
    public void cleanupCompletedTasks() {
        log.info("开始清理已完成的回滚任务日志");
        
        // 删除7天前已成功或失败的任务
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        
        List<Long> completedIds = operationLogMapper.selectList(
            new LambdaQueryWrapper<OrderOperationLog>()
                .in(OrderOperationLog::getOperationStatus, 
                    OperationStatus.SUCCESS.getValue(), 
                    OperationStatus.FAILED.getValue())
                .lt(OrderOperationLog::getCreatedTime, threshold)
                .last("LIMIT 1000")
        ).stream().map(OrderOperationLog::getId).collect(Collectors.toList());
        
        if (!completedIds.isEmpty()) {
            operationLogMapper.deleteBatchIds(completedIds);
            log.info("清理了 {} 条已完成的任务日志", completedIds.size());
        } else {
            log.debug("无需清理的任务日志");
        }
    }
}
