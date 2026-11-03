package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人事项进度/提醒/备注（仅本人可见）。
 */
@Data
@TableName("compliance_user_progress")
public class ComplianceUserProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事项 id */
    private Long itemId;

    /** 所属用户 */
    private Long userId;

    /** 个人进度：UNPROCESSED/PROCESSING/COMPLETED（仅本人可改） */
    private String progressStatus;

    /** 阻塞原因（预设或自定义） */
    private String blockReason;

    /** 个人备注（仅本人） */
    private String remark;

    /** 个人提醒开关 */
    private Integer remindEnabled;

    /** 提醒提前天数（1-30） */
    private Integer remindDays;

    /** 个人提醒最近推送日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastDueNotified;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
