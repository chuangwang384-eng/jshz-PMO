package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合规规范模板版本（发布后不可原地改写）（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_template_version")
public class ComplianceTemplateVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源知识文档 */
    private Long documentId;

    /** 来源文档版本 */
    private Long sourceDocumentVersionId;

    /** 同文档模板修订号 */
    private Integer revision;

    /** DRAFT/PUBLISHED/RETIRED */
    private String state;

    /** 草稿所有者（上传者） */
    private Long ownerUserId;

    /** 发布人 */
    private Long publishedBy;

    private String title;

    /** 适用说明 */
    private String applicability;

    /** 乐观锁版本 */
    private Integer rowVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
