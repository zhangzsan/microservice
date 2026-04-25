package com.example.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.constant.OperationType;
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

/**
 * 回滚任务补偿定时任务
 * 职责说明:
 * compensateMissingRollbackTasks - 补偿缺失的回滚任务记录
 * 场景: 订单已超时(TIMEOUT),但order_operation_log表中没有对应记录
 * 原因: DB异常导致记录创建失败等极端情况
 * <p>
 * 注意:
 * - 正常情况下,OrderTimeoutConsumer会在更新订单状态时同步创建回滚任务记录
 * - 本方法只处理极端异常情况(如DB异常导致记录创建失败)
 * - 回滚任务的执行由 StockCompensationTask 负责
 * - StockCompensationTask 会扫描 PROCESSING/FAILED 状态的任务并触发执行
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
     * 补偿机制: 每5分钟扫描一次,检测超时订单是否有对应的回滚任务记录
     * <p>
     * 说明:
     * - 正常情况下,OrderTimeoutConsumer会在更新订单状态时同步创建回滚任务记录
     * - 本方法只处理极端异常情况(如DB异常导致记录创建失败)
     * - 降低扫描频率至5分钟,减少数据库压力
     */
    @Scheduled(fixedDelay = 300000)
    public void compensateMissingRollbackTasks() {
        log.debug("开始扫描缺失的回滚任务");

        // 查询15分钟前超时的订单(给正常流程留足够时间)
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(15);

        List<Order> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderStatus.TIMEOUT.getValue())
                        .lt(Order::getUpdatedTime, timeoutThreshold)
                        .last("LIMIT 50")  // 每次最多处理50个订单
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
                OrderOperationLog orderOperationLog = operationLogMapper.selectOne(
                        new LambdaQueryWrapper<OrderOperationLog>()
                                .eq(OrderOperationLog::getOrderNo, order.getOrderNo())
                                .eq(OrderOperationLog::getOperationType, OperationType.TIMEOUT_CANCEL.getValue())
                );

                if (orderOperationLog != null) {
                    skippedCount++;
                    continue;
                }

                // 不存在回滚任务,补偿创建
                log.warn("检测到缺失的回滚任务,补偿创建 - 订单号: {}", order.getOrderNo());

                OrderTimeoutMessage message = new OrderTimeoutMessage();
                message.setOrderNo(order.getOrderNo());
                message.setProductId(order.getProductId());
                message.setQuantity(order.getQuantity());
                message.setUserId(order.getUserId());
                message.setAmount(order.getAmount());

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
}
