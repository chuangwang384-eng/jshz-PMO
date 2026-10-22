package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流进度记录实体（对应数据库表 approval_record）。
 * action：SUBMIT / APPROVE / REJECT。
 */
@Data
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 审批单ID */
    @JsonProperty("requestId")
    private Long requestId;

    /** 节点名称 */
    @JsonProperty("nodeName")
    private String nodeName;

    /** 动作：SUBMIT / APPROVE / REJECT */
    @JsonProperty("action")
    private String action;

    /** 操作人 */
    @JsonProperty("operator")
    private String operator;

    /** 说明 / 意见 */
    @JsonProperty("comment")
    private String comment;

    /** 记录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
