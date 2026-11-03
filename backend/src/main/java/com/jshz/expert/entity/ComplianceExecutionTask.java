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
 * 个人执行任务（快照+答案+计划+进度）（REQ/DES-COMPLIANCE-V4）。
 */
@Data
@TableName("compliance_execution_task")
public class ComplianceExecutionTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    /** 模板节点 code */
    private String sourceNodeCode;

    /** 创建时节点快照（JSON） */
    private String snapshot;

    /** 本次填写结果（JSON） */
    private String answer;

    /** APPLICABLE/NOT_APPLICABLE */
    private String applicability;

    /** 本次计划到期日（空=待排期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    /** HIGH/MEDIUM/LOW */
    private String priority;

    /** UNPROCESSED/PROCESSING/COMPLETED */
    private String progress;

    /** 私人备注（仅本人，≤2000 字） */
    private String note;

    /** 等待跟进：在等什么 */
    private String waitingReason;

    /** 等待跟进：下一步行动 */
    private String nextAction;

    /** 跟进复查日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reviewDate;

    private Integer remindEnabled;

    private Integer remindDays;

    /** 提醒计划版本（改期/取消递增失效旧节点） */
    private Integer scheduleRevision;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    @Version
    private Integer rowVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
