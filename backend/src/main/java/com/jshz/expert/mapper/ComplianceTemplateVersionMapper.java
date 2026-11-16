package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceTemplateVersion;
import org.apache.ibatis.annotations.Mapper;

/** 合规规范模板版本（发布后不可原地改写） Mapper。 */
@Mapper
public interface ComplianceTemplateVersionMapper extends BaseMapper<ComplianceTemplateVersion> {
}
