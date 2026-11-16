package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合规规范节点（分组+细项，最多两级）（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_template_node")
public class ComplianceTemplateNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    /** 父分组节点（NULL=顶层分组） */
    private Long parentId;

    /** 节点稳定标识（同版本唯一） */
    private String nodeCode;

    private String title;

    /** 阶段（沿用 v3 枚举） */
    private String stage;

    /** CHECK/SINGLE/MULTI/TEXT */
    private String inputType;

    /** 操作说明 */
    private String instruction;

    /** 材料清单 */
    private String materials;

    /** 完成判定/验收标准 */
    private String acceptance;

    /** 规范时限（原文要求） */
    private String timeRule;

    /** 是否必做 */
    private Integer required;

    /** 原文引用（页码/章节） */
    private String sourceAnchor;

    /** 前置选项 id（简单分支） */
    private Long conditionOptionId;

    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
