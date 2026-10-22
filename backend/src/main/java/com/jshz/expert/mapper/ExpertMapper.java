package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Expert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 专家信息 Mapper。
 * 复杂检索（按领域/标签/全文）可在 resources/mapper/ExpertMapper.xml 中扩展。
 */
@Mapper
public interface ExpertMapper extends BaseMapper<Expert> {
}
