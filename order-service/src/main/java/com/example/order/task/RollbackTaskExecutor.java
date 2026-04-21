package com.example.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 
 * 职责说明:
 * compensateMissingRollbackTasks - 补偿缺失的回滚任务记录
 *    场景: 订单已超时(TIMEOUT),但order_operation_log表中没有对应记录
 *    原因: createRollbackTask调用失败(网络异常、DB异常等)

 * 注意:
 * - 回滚任务的执行由 StockCompensationTask 负责
 * - StockCompensationTask 会扫描 PROCESSING/FAILED 状态的任务并触发执行
 * - 本类只负责任务记录的补偿创建,不负责执行
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
     * 补偿机制: 每2分钟扫描一次,检测超时订单是否有对应的回滚任务记录
     * 
     * 说明:
     * - RocketMQ消费者处理延迟通常在秒级
     * - 10分钟阈值已经很大,2分钟扫描频率足够
     * - 降低频率可减少数据库压力
     */
    @Scheduled(fixedDelay = 120000)
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
}
