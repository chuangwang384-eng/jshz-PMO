package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目×来源文档绑定（Owner 2026-11-10 需求规则 4）：首次推送事务内写入，
 * 之后仅允许同一文档，本期无换绑入口。
 */
@Data
@TableName("compliance_project_doc_binding")
public class ComplianceProjectDocBinding {

    /** 项目 ID（主键：一项目至多绑定一份文档） */
    @TableId(type = IdType.INPUT)
    private Long projectId;

    private Long documentId;

    /** 绑定时文档名快照 */
    private String docTitle;

    private String bindBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bindTime;
}
