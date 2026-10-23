package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.KnowledgeDocVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档版本历史 Mapper（知识星球）。
 */
@Mapper
public interface KnowledgeDocVersionMapper extends BaseMapper<KnowledgeDocVersion> {
}
