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

    private String defaultValue;

    private String unit;

    private String remark;

    private Integer isActive;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
