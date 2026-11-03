package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceExecution;
import org.apache.ibatis.annotations.Mapper;

/** 个人执行清单（所有人为隔离边界） Mapper。 */
@Mapper
public interface ComplianceExecutionMapper extends BaseMapper<ComplianceExecution> {
}
