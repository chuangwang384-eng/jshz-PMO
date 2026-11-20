package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目阶段表单（2026-11-20 新增）。
 * 会前财务资料上传等阶段按项目类型差异化填写的表单数据，一项目一阶段一份，
 * form_data 存 JSON，整包覆盖保存，不做历史版本。
 * 对应数据库表 project_stage_form。
 */
@Data
@TableName("project_stage_form")
public class ProjectStageForm {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 阶段 project_phase.id */
    private Long phaseId;

    /** 表单数据 JSON（按项目类型差异化字段） */
    private String formData;

    /** 创建人 sys_user.id */
    private Long createBy;

    /** 最后修改人 sys_user.id */
    private Long updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
