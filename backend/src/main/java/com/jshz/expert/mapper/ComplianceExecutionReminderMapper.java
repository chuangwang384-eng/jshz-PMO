package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceExecutionReminder;
import org.apache.ibatis.annotations.Mapper;

/** 执行任务实例级提醒节点（revision 幂等） Mapper。 */
@Mapper
public interface ComplianceExecutionReminderMapper extends BaseMapper<ComplianceExecutionReminder> {
}
