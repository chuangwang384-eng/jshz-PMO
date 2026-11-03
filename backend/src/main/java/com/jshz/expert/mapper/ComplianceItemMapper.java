package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合规事项主表（全局事实，仅维护人可写） Mapper。
 */
@Mapper
public interface ComplianceItemMapper extends BaseMapper<ComplianceItem> {
}
