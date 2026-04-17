package com.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.entity.TransactionMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionMessageMapper extends BaseMapper<TransactionMessage> {
}
