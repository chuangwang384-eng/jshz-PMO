package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ProjectStageForm;
import org.apache.ibatis.annotations.Mapper;

/**
 * ProjectStageForm Mapper（项目阶段表单，一项目一阶段一份）。
 */
@Mapper
public interface ProjectStageFormMapper extends BaseMapper<ProjectStageForm> {
}
