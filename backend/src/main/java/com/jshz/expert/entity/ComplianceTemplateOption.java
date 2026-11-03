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
 * 模板候选选项（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_template_option")
public class ComplianceTemplateOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long nodeId;

    private String optionCode;

    private String label;

    /** 该选项适用条件/说明 */
    private String guidance;

    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
