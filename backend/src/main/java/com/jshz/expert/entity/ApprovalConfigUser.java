package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流具体审批人配置实体（对应数据库表 approval_config_user，R2 指定具体审批人）。
 * 一个审批流（approval_config.id）可配置多名具体审批人；或签语义：任一名审批人
 * approve 即终态。提交申请时从该配置生成审批人快照（application.approver_snapshot）。
 */
@Data
@TableName("approval_config_user")
public class ApprovalConfigUser {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 审批流配置ID（approval_config.id） */
    @JsonProperty("configId")
    private Long configId;

    /** 审批人用户ID（sys_user.id） */
    @JsonProperty("userId")
    private Long userId;

    /** 审批人用户名快照 */
    @JsonProperty("username")
    private String username;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
