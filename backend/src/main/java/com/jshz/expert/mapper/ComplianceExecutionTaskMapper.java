package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceExecutionTask;
import org.apache.ibatis.annotations.Mapper;

/** 个人执行任务（快照+答案+计划+进度） Mapper。 */
@Mapper
public interface ComplianceExecutionTaskMapper extends BaseMapper<ComplianceExecutionTask> {
}
