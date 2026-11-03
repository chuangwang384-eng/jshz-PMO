package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合规提醒记录（唯一约束幂等）。
 */
@Data
@TableName("compliance_reminder_log")
public class ComplianceReminderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    /** due_before / due_today / overdue / assignee_due / done_notify 等 */
    private String reminderType;

    /** 计划提醒时间（自然日） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentTime;

    /** PENDING/SENT/FAILED/SKIPPED */
    private String sendStatus;

    /** 重试次数（上限 3） */
    private Integer retryCount;

    /** 是否仅工作日（预留） */
    private Integer workdayOnly;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
