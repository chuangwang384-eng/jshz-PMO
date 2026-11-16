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
 * 执行任务实例级提醒节点（revision 幂等）（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_execution_reminder")
public class ComplianceExecutionReminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private Long taskId;

    private Long recipientUserId;

    /** due_before / due_today / review_due */
    private String kind;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;

    /** 计划版本 */
    private Integer scheduleRevision;

    /** PENDING/SENDING/SENT/FAILED/CANCELLED */
    private String state;

    /** 重试次数（上限 3） */
    private Integer attemptCount;

    /** 实际发送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentTime;

    /** 站内消息 id */
    private Long notificationId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
