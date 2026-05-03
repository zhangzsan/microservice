package com.example.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.constant.OperationStatus;
import com.example.order.entity.OrderOperationLog;
import com.example.order.mapper.OrderOperationLogMapper;
import com.example.order.service.StockRollbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

//优化定时补偿任务-批量处理
@Component
@Slf4j
public class StockCompensationTask {

    @Autowired
    private OrderOperationLogMapper operationLogMapper;

    @Autowired
    private StockRollbackService rollbackService;

    /**
     * 执行回滚补偿任务
     * 说明:
     * - 扫描 PROCESSING/FAILED状态且到达重试时间的任务
     * - 批量调用 executeBatchRollback 执行回滚
     * - 30秒频率保证回滚及时性,同时避免频繁扫描
     */
    @Scheduled(fixedDelay = 30000)
    public void compensateFailedRollbacks() {
        log.info("开始执行回滚补偿任务");
        List<OrderOperationLog> failedLogs = operationLogMapper.selectList(new LambdaQueryWrapper<OrderOperationLog>()
                .in(OrderOperationLog::getOperationStatus, OperationStatus.PROCESSING.getValue(), OperationStatus.FAILED.getValue())
                .le(OrderOperationLog::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OrderOperationLog::getNextRetryTime)
                .last("LIMIT 100"));

        if (failedLogs.isEmpty()) {
            log.debug("无需补偿的任务");
            return;
        }
        log.info("发现 {} 个需要补偿的任务", failedLogs.size());
        List<Long> logIds = failedLogs.stream().map(OrderOperationLog::getId).collect(Collectors.toList());
        try {
            rollbackService.executeBatchRollback(logIds);
            log.info("回滚补偿任务执行完成");
        } catch (Exception e) {
            log.error("批量补偿任务执行异常", e);
        }
    }
}
