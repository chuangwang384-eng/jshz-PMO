package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阶段-模板绑定（管理员可配置，required 必填标记，推进校验用）。
 * 对应数据库表 project_phase_template_rel。
 */
@Data
@TableName("project_phase_template_rel")
public class ProjectPhaseTemplateRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 阶段 project_phase.id */
    private Long phaseId;

    /** 模板 project_template.id */
    private Long templateId;

    /** 项目类型ID，0=全局兜底绑定（20261102 配置板块 v2） */
    private Long typeId;

    /** 是否必填：1必填 0选填 */
    private Integer required;

    /** 排序（小在前） */
    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
