package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceTemplateNode;
import org.apache.ibatis.annotations.Mapper;

/** 合规规范节点（分组+细项，最多两级） Mapper。 */
@Mapper
public interface ComplianceTemplateNodeMapper extends BaseMapper<ComplianceTemplateNode> {
}
