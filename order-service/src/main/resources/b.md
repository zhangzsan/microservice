1. 创建消息
   ├─ INSERT t_transaction_message (status=PENDING)
   └─ ZADD tx_msg:pending <timestamp> <message_id>

2. 定时扫描（每5秒）
   ├─ ZRANGEBYSCORE tx_msg:pending 0 now
   ├─ 获取分布式锁
   ├─ 查询DB确认状态
   ├─ 发送到RocketMQ
   ├─ UPDATE status=SENT
   └─ ZREM tx_msg:pending <message_id>

3. 发送失败
   ├─ CAS更新 retry_count + 1
   ├─ 计算下次重试时间
   ├─ UPDATE next_retry_time
   └─ ZADD tx_msg:pending <new_timestamp> <message_id>

4. 对账补偿（每5分钟）
   ├─ 对比Redis和DB状态
   └─ 清理不一致数据

5. 达到最大重试次数
   └─ UPDATE status=FAILED (需人工介入)
