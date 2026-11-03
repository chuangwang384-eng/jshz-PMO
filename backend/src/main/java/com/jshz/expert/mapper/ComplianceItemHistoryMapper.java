package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceItemHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事项修改历史（字段变更+审计动作） Mapper。
 */
@Mapper
public interface ComplianceItemHistoryMapper extends BaseMapper<ComplianceItemHistory> {
}
