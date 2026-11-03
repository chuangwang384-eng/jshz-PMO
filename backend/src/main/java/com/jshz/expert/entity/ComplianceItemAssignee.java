package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 事项责任人关联（多人）。
 */
@Data
@TableName("compliance_item_assignee")
public class ComplianceItemAssignee {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事项 id */
    private Long itemId;

    /** 责任人 sys_user.id */
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
