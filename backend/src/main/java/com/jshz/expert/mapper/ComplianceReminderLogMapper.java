package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceReminderLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合规提醒记录（唯一约束幂等） Mapper。
 */
@Mapper
public interface ComplianceReminderLogMapper extends BaseMapper<ComplianceReminderLog> {
}
