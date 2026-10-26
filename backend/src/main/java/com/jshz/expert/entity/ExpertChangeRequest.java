package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 专家信息修改审批单实体（对应数据库表 expert_change_request）。
 * status：0 待审批 / 1 已通过 / 2 已驳回。
 */
@Data
@TableName("expert_change_request")
public class ExpertChangeRequest {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 专家ID */
    @JsonProperty("expertId")
    private Long expertId;

    /** 专家姓名快照 */
    @JsonProperty("expertName")
    private String expertName;

    /** 申请人用户名 */
    @JsonProperty("applicant")
    private String applicant;

    /** 变更内容 JSON（字段中文键 -> 新值） */
    @JsonProperty("changeData")
    private String changeData;

    /** 申请原因 */
    @JsonProperty("reason")
    private String reason;

    /** 申请类型：0=修改申请，1=新增专家申请 */
    @JsonProperty("requestType")
    private Integer requestType;

    /** 审批状态：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 */
    @JsonProperty("status")
    private Integer status;

    /** 审批人用户名 */
    @JsonProperty("approveBy")
    private String approveBy;

    /** 审批意见 */
    @JsonProperty("approveComment")
    private String approveComment;

    /** 申请时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("approveTime")
    private LocalDateTime approveTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    /** 变更内容解析后的对象（非数据库列，仅响应展示用） */
    @TableField(exist = false)
    @JsonProperty("changes")
    private Map<String, Object> changes;

    /** 申请类型文本：新增专家 / 修改申请（非数据库列，仅响应展示用） */
    @TableField(exist = false)
    @JsonProperty("requestTypeText")
    private String requestTypeText;

    /** 状态文本（非数据库列，仅响应展示用） */
    @TableField(exist = false)
    @JsonProperty("statusText")
    private String statusText;

    /** 审批流进度记录（非数据库列，按时间升序） */
    @TableField(exist = false)
    @JsonProperty("records")
    private List<ApprovalRecord> records;
}
