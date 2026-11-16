package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合规事项主表（全局事实，仅维护人可写）。
 */
@Data
@TableName("compliance_item")
public class ComplianceItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 跨版本稳定标识（首次生成后不变） */
    private String itemCode;

    /** 所属文档 knowledge_document.id */
    private Long documentId;

    /** 所属文档版本 knowledge_doc_version.id */
    private Long documentVersionId;

    /** 上一版本同一事项 id（版本血缘） */
    private Long previousItemId;

    /** 版本变更类型：新增/修改/删除/不变 */
    private String changeType;

    /** 事项标题 */
    private String title;

    /** 要点内容 */
    private String content;

    /** 阶段：INITIATION/FINANCE/EXECUTION/VERIFICATION/SPECIAL/OTHER */
    private String stage;

    /** 时间要求（文本） */
    private String timeNote;

    /** 到期日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    /** 优先级：HIGH/MEDIUM/LOW */
    private String priority;

    /** 全局状态：PENDING/IN_PROGRESS/CONFIRMING/COMPLETED（仅维护人可改） */
    private String status;

    /** 1=待确认草稿 0=已发布 */
    private Integer isDraft;

    /** 跨版本匹配待维护人确认 */
    private Integer pendingConfirm;

    /** 1=已按文档撤下（历史保留，记事本/列表不再展示） Owner 2026-11-10 */
    private Integer withdrawn;

    /** 撤下操作人 */
    private String withdrawnBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime withdrawnTime;

    /** 预留弱关联 project.id */
    private Long projectId;

    /** 全局备注 */
    private String remark;

    /** 全局责任人到期提醒最近推送日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastDueNotified;

    /** 最后更新人用户名 */
    private String updateByName;

    /** 创建人用户名 */
    private String createByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
