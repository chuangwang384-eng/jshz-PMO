package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_budget_quote")
public class ProjectBudgetQuote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer versionNo;
    @JsonIgnore
    private String requestKey;
    private Long docId;
    @JsonIgnore
    private String snapshot;
    private String clientNote;
    private Long createdBy;
    private LocalDateTime createTime;
    private String customerName;
    private LocalDateTime customerConfirmedAt;
    private String customerRemark;
    private Long evidenceDocId;
    private Long recordedBy;
    private LocalDateTime recordedAt;
}
