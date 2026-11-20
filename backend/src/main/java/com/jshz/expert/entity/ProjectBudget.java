package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目会议预算/报价单（2026-11-16「会议预算制作」阶段，一项目一份）。
 * category 为项目大类快照（ONLINE 线上简化报价单 / OFFLINE 线下六类费用明细）。
 */
@Data
@TableName("project_budget")
public class ProjectBudget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** ONLINE / OFFLINE（保存时自 project 快照） */
    private String category;

    /** DRAFT / CONFIRMED */
    private String status;

    /** 明细合计（服务端计算） */
    private BigDecimal totalAmount;

    /** 成本合计（2026-11-20 成本单维度，服务端计算） */
    private BigDecimal costTotal;

    /** 服务费率（历史结算单口径 6%） */
    private BigDecimal serviceRate;

    /** 增值税率（历史结算单口径 6%） */
    private BigDecimal taxRate;

    private String remark;
    private String estimateInputs;

    private Long createdBy;

    private Long updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
