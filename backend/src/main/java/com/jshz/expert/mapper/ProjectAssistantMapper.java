package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ProjectAssistant;
import org.apache.ibatis.annotations.Mapper;

/**
 * ProjectAssistant Mapper（项目协助人，多选）。
 */
@Mapper
public interface ProjectAssistantMapper extends BaseMapper<ProjectAssistant> {
}
