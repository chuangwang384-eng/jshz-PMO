package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目类型-流程阶段关联（类型自定义阶段序列，sort 小在前）。
 * 对应数据库表 project_type_phase_rel；同一类型内阶段不可重复（uk_type_phase）。
 */
@Data
@TableName("project_type_phase_rel")
public class ProjectTypePhaseRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目类型 project_type.id */
    private Long typeId;

    /** 流程阶段 project_phase.id */
    private Long phaseId;

    /** 阶段顺序（小在前） */
    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
