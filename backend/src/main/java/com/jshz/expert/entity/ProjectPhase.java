package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目流程阶段配置（可配置，项目当前阶段=状态）。
 * 对应数据库表 project_phase；种子 6 阶段：立项/预算/筹备/进行中/结算/已完成。
 */
@Data
@TableName("project_phase")
public class ProjectPhase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 阶段名称 */
    private String name;

    /** 排序（小在前） */
    private Integer sort;

    /** 1启用 0停用（停用不再可选） */
    private Integer isActive;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
