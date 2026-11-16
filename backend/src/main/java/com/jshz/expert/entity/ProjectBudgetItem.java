package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目预算明细行。section：HOTEL 酒店 / TRAFFIC 交通 / BUILD 搭建 / MATERIAL 物料 /
 * MEDIA 媒体 / GIFT 礼品 / STAFF 人员 / ONLINE_QUOTE 线上报价。
 * item_key 关联 project_budget_fee_standard.fee_key（预填项），NULL=手工行。
 */
@Data
@TableName("project_budget_item")
public class ProjectBudgetItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long budgetId;

    /** 冗余项目 id，便于按项目查 */
    private Long projectId;

    private String section;

    private String itemKey;

    private String name;

    private BigDecimal quantity;

    private String unit;

    private BigDecimal unitPrice;

    /** quantity * unit_price（服务端计算） */
    private BigDecimal amount;

    private String remark;

    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
