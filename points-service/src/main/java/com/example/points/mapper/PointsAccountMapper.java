package com.example.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.points.entity.PointsAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointsAccountMapper extends BaseMapper<PointsAccount> {
    
    /**
     * 原子性增加用户积分
     * 使用SQL级别的原子操作,防止并发问题
     * 
     * @param userId 用户ID
     * @param points 要增加的积分数
     * @return 影响的行数
     */
    @Update("UPDATE points_account " +
            "SET total_points = total_points + #{points}, " +
            "    available_points = available_points + #{points}, " +
            "    updated_time = NOW() " +
            "WHERE user_id = #{userId}")
    int increasePoints(@Param("userId") Long userId, @Param("points") Integer points);
    
    /**
     * 如果用户不存在则初始化积分账户
     *
     * @param userId 用户ID
     */
    @Update("INSERT INTO points_account (user_id, total_points, available_points, frozen_points, created_time, updated_time) " +
            "VALUES (#{userId}, 0, 0, 0, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE user_id = #{userId}")
    void initAccountIfNotExists(@Param("userId") Long userId);
}
