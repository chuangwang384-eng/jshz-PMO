package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceItemAssignee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事项责任人关联（多人） Mapper。
 */
@Mapper
public interface ComplianceItemAssigneeMapper extends BaseMapper<ComplianceItemAssignee> {
}
