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
 * 通用申请实体（对应数据库表 application，「我的申请」改版）。
 * 各业务线申请的公共字段存本表，业务差异存 biz_data JSON：
 * 专家申请 biz_data = {expertId, expertName, changes, reason, oldData?}
 * （照片 TMP: 前缀语义保留；oldData 为修改申请提交时的专家旧值快照，新增申请无）。
 * status：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 退回修改（R4 退回修改后可重新提交）。
 * flowKey：多流程标识（EXPERT / CONTRACT，对应 approval_config.flow_key；存量单为空）。
 * approverSnapshot：提交时由流程配置生成的具体审批人快照 JSON 数组（或签语义）。
 */
@Data
@TableName("application")
public class Application {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 申请人用户 ID（存量迁移按用户名回查，可空） */
    @JsonProperty("applicantId")
    private Long applicantId;

    /** 申请人用户名 */
    @JsonProperty("applicant")
    private String applicant;

    /** 类型编码：EXPERT_MODIFY / EXPERT_CREATE / CONTRACT（未来扩展） */
    @JsonProperty("applicationType")
    private String applicationType;

    /** 流程标识（多流程，对应 approval_config.flow_key：EXPERT / CONTRACT；存量单为空） */
    @JsonProperty("flowKey")
    private String flowKey;

    /** 审批状态：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 退回修改 */
    @JsonProperty("status")
    private Integer status;

    /** 业务详情 JSON（专家申请：expertId/expertName/changes/reason） */
    @JsonProperty("bizData")
    private String bizData;

    /** 审批人用户名 */
    @JsonProperty("approveBy")
    private String approveBy;

    /** 审批意见（驳回必填，通过可空） */
    @JsonProperty("approveComment")
    private String approveComment;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("approveTime")
    private LocalDateTime approveTime;

    /** 退回修改意见（status=4 时记录） */
    @JsonProperty("returnComment")
    private String returnComment;

    /** 当前审批人（B2 转办后有值；为空=未转办，任何拥有 approval:audit 的用户可审批） */
    @JsonProperty("currentAssignee")
    private String currentAssignee;

    /** 加签审批人（B2 加签后有值；或签语义：与原审批人任一审批即终态） */
    @JsonProperty("extraApprover")
    private String extraApprover;

    /** 审批人快照 JSON 数组 [{"id":1,"username":"jshz"}]（提交时由流程配置生成；空数组=任意有审批权限的用户可审；存量单为空） */
    @JsonProperty("approverSnapshot")
    private String approverSnapshot;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    // ==================== biz_data 解析后的展示字段（非数据库列） ====================

    /** 专家ID（biz_data.expertId，新增申请通过后回填） */
    @TableField(exist = false)
    @JsonProperty("expertId")
    private Long expertId;

    /** 专家姓名快照（biz_data.expertName） */
    @TableField(exist = false)
    @JsonProperty("expertName")
    private String expertName;

    /** 申请原因（biz_data.reason） */
    @TableField(exist = false)
    @JsonProperty("reason")
    private String reason;

    /** 变更内容解析对象（biz_data.changes，照片值已友好化为「已更换新照片」；非数据库列） */
    @TableField(exist = false)
    @JsonProperty("changes")
    private Map<String, Object> changes;

    /** 旧值快照解析对象（biz_data.oldData，提交修改申请时专家全量字段快照；非管理员身份证号/银行卡号脱敏；非数据库列） */
    @TableField(exist = false)
    @JsonProperty("oldData")
    private Map<String, Object> oldData;

    /** 照片字段原始值（TMP: 临时名或生效后回填的归档文件名，供对比弹窗加载新照片缩略图；非数据库列） */
    @TableField(exist = false)
    @JsonProperty("photoChanges")
    private Map<String, Object> photoChanges;

    /** 申请类型数值（兼容旧前端：0=修改 / 1=新增；非数据库列） */
    @TableField(exist = false)
    @JsonProperty("requestType")
    private Integer requestType;

    /** 申请类型文本（非数据库列） */
    @TableField(exist = false)
    @JsonProperty("requestTypeText")
    private String requestTypeText;

    /** 状态文本（非数据库列） */
    @TableField(exist = false)
    @JsonProperty("statusText")
    private String statusText;

    /** 审批流进度记录（按时间升序，关联 approval_record.application_id；非数据库列） */
    @TableField(exist = false)
    @JsonProperty("records")
    private List<ApprovalRecord> records;

    /**
     * 当前处理人展示文案（R2，非数据库列）：待审批单取 current_assignee → extra_approver → 审批人快照；
     * 退回修改单固定为「待申请人修改」；其余状态为空。由 ApplicationService.fillView 派生。
     */
    @TableField(exist = false)
    @JsonProperty("currentAssigneeName")
    private String currentAssigneeName;

    /**
     * 待审批停留时长（R2，非数据库列，单位小时，向下取整）：自最近一次提交/重新提交记录起算，
     * 仅 status=0 有值，其余为 null。前端按「已等待 N 小时」展示。
     */
    @TableField(exist = false)
    @JsonProperty("pendingHours")
    private Long pendingHours;
}
