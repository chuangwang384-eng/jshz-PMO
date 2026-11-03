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
 * 清单/任务私人操作事件（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_execution_event")
public class ComplianceExecutionEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    /** 任务级事件（清单级为 NULL） */
    private Long taskId;

    private Long actorUserId;

    /** CREATE/START/PROGRESS/NOTE/DUE/PRIORITY/WAITING/WAIT_CLEAR/COMPLETE/REOPEN/PAUSE/ARCHIVE/REUSE 等 */
    private String eventType;

    /** 字段差异/私人备注历史（JSON） */
    private String payload;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
