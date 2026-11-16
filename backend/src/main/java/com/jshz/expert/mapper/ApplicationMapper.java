package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Application;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 通用申请 Mapper（application 表）。
 */
@Mapper
public interface ApplicationMapper extends BaseMapper<Application> {

    /**
     * 条件状态更新（H2 并发抢占）：仅当当前状态等于 {@code expectedStatus} 时才改为 {@code targetStatus}。
     *
     * <p>返回受影响行数：1 表示抢占成功，0 表示该申请单已被其他事务处理（并发审批/撤回）。
     * 调用方必须依据返回值判定，不得先查状态再无条件更新。{@code update_time} 由表定义
     * {@code ON UPDATE CURRENT_TIMESTAMP} 自动维护，无需显式赋值。</p>
     */
    @Update("UPDATE application SET status = #{targetStatus} "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expectedStatus") int expectedStatus,
                              @Param("targetStatus") int targetStatus);
}
