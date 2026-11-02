package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企微同步审计日志（2026-10-27 企微通讯录同步）。
 * 对应表 wecom_sync_log；每次手动/定时同步一条。
 */
@Data
@TableName("wecom_sync_log")
public class WecomSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MANUAL/SCHEDULED/DRYRUN */
    @JsonProperty("triggerType")
    private String triggerType;

    @JsonProperty("operator")
    private String operator;

    /** RUNNING/SUCCESS/FAILED */
    @JsonProperty("status")
    private String status;

    @JsonProperty("fetchedUsers")
    private Integer fetchedUsers;

    @JsonProperty("createdUsers")
    private Integer createdUsers;

    @JsonProperty("updatedUsers")
    private Integer updatedUsers;

    @JsonProperty("disabledUsers")
    private Integer disabledUsers;

    @JsonProperty("boundUsers")
    private Integer boundUsers;

    @JsonProperty("unboundLeft")
    private Integer unboundLeft;

    @JsonProperty("errorMsg")
    private String errorMsg;

    /** DryRun 变更清单 JSON */
    @JsonProperty("detail")
    private String detail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("startTime")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("endTime")
    private LocalDateTime endTime;
}
