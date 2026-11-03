package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 事项修改历史（字段变更+审计动作）。
 */
@Data
@TableName("compliance_item_history")
public class ComplianceItemHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    /** 变更字段或审计动作（草稿发布/下架/恢复/删除/匹配确认/责任人等） */
    private String fieldName;

    private String oldValue;

    private String newValue;

    private Long operatorId;

    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;

    /** 变更原因（可选填） */
    private String changeReason;
}
