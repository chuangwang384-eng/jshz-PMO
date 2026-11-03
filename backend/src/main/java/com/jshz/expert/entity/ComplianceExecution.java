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
 * 个人执行清单（所有人为隔离边界）（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_execution")
public class ComplianceExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 清单所有人 */
    private Long ownerUserId;

    /** 关联系统项目（可空=个人用途） */
    private Long projectId;

    /** 固定模板版本 */
    private Long templateVersionId;

    /** 来源文档（冗余快照） */
    private Long sourceDocumentId;

    private String name;

    /** DRAFT/ACTIVE/COMPLETED/PAUSED/ARCHIVED */
    private String state;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;

    /** TEMPLATE/LEGACY */
    private String sourceKind;

    /** 旧数据导入源标识（LEGACY 必填） */
    private String sourceKey;

    /** 创建幂等键 */
    private String idempotencyKey;

    @Version
    private Integer rowVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
