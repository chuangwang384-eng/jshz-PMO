package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流配置实体（对应数据库表 approval_config）。
 * 流程键 flow_key='EXPERT_MODIFY' 为专家信息修改审批流。
 */
@Data
@TableName("approval_config")
public class ApprovalConfig {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 流程标识 */
    @JsonProperty("flowKey")
    private String flowKey;

    /** 流程名称 */
    @JsonProperty("flowName")
    private String flowName;

    /** 是否启用审批流（1 启用 / 0 停用） */
    @JsonProperty("enabled")
    private Integer enabled;

    /** 审批人角色 */
    @JsonProperty("approverRole")
    private String approverRole;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;
}
