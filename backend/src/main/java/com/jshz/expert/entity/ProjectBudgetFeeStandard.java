package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议预算收费标准默认值（管理员「项目配置-收费标准」维护）。
 * default_value：单值如 "500"；区间存 "低,高" 如 "300,400"；NULL=视需求报价无默认。
 */
@Data
@TableName("project_budget_fee_standard")
public class ProjectBudgetFeeStandard {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定键，预算明细 item_key 引用 */
    private String feeKey;

    private String name;

    /** 适用大类：ONLINE 线上 / OFFLINE 线下（2026-11-16 分场景，线上项目点选生成报价单） */
    private String scene;

    /** 类目：ELEC电子物料/PRINT小型制作/ONLINE线上直播/BUILD大型搭建/STAFF人员服务/SPECIAL非常规（2026-11-20 收费标准库） */
    private String catalog;

    /** 小类（模板类别列，如设计类） */
    private String category;

    /** 详细内容/规格说明 */
    private String detail;

    /** 规格/时效（如"2个工作日"） */
    private String spec;

    private String defaultValue;

    /** 成本价（成本单/供应商价，2026-11-20） */
    private java.math.BigDecimal costPrice;

    /** 最低利润要求（报价-成本下限） */
    private java.math.BigDecimal minProfit;

    private String unit;

    private String remark;

    private Integer isActive;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
