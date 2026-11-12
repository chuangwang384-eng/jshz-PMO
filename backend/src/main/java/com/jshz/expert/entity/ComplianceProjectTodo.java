package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目合规待办（Owner 2026-11-10 需求规则 5）：推送时冻结标题/内容/来源引用快照，
 * 来源更新、撤下、取消收藏和个人隐藏均不改变快照与处理状态。
 */
@Data
@TableName("compliance_project_todo")
public class ComplianceProjectTodo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 来源稳定事项 ID（project_id+source_item_id 唯一，重复推送去重依据） */
    private Long sourceItemId;

    private Long documentId;

    private Long documentVersionId;

    /** 推送时文档名快照 */
    private String docTitle;

    /** 推送时标题快照 */
    private String title;

    /** 推送时内容快照 */
    private String content;

    /** 来源引用（版本锚点） */
    private String sourceAnchor;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean sourceDeleted;

    /** PENDING/DONE */
    private String status;

    private String handleBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 乐观锁序号 */
    @Version
    private Integer rowVersion;
}
