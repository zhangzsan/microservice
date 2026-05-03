package com.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.entity.Order;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends MPJBaseMapper<Order> {
}