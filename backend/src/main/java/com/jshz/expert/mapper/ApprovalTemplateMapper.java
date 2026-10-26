package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ApprovalTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批申请字段模板 Mapper（需求 R-B4）。
 */
@Mapper
public interface ApprovalTemplateMapper extends BaseMapper<ApprovalTemplate> {
}
