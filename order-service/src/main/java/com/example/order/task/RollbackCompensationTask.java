package com.example.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.constant.OperationStatus;
import com.example.order.entity.OrderOperationLog;
import com.example.order.mapper.OrderOperationLogMapper;
import com.example.order.service.ResourceRollbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class RollbackCompensationTask {

    @Autowired
    private OrderOperationLogMapper operationLogMapper;

    @Autowired
    private ResourceRollbackService rollbackService;

    @Scheduled(fixedDelay = 60000)
    public void compensateFailedRollbacks() {
        log.info("开始执行回滚补偿任务");

        List<OrderOperationLog> failedLogs = operationLogMapper.selectList(
            new LambdaQueryWrapper<OrderOperationLog>()
                .in(OrderOperationLog::getOperationStatus, 
                    OperationStatus.PROCESSING.getValue(), 
                    OperationStatus.FAILED.getValue())
                .le(OrderOperationLog::getNextRetryTime, LocalDateTime.now())
                .last("LIMIT 100"));

        if (failedLogs.isEmpty()) {
            log.debug("无需补偿的任务");
            return;
        }

        log.info("发现 {} 个需要补偿的任务", failedLogs.size());

        for (OrderOperationLog blog : failedLogs) {
            try {
                rollbackService.executeRollback(blog.getId());
            } catch (Exception e) {
                log.error("补偿任务执行异常，ID: {}", blog.getId(), e);
            }
        }

        log.info("回滚补偿任务执行完成");
    }
}
