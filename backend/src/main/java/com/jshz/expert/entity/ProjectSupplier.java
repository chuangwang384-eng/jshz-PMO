package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目-供应商关联（全局一份；用途按类型自动带出；移出留痕）。
 * 对应数据库表 project_supplier。
 */
@Data
@TableName("project_supplier")
public class ProjectSupplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 供应商 supplier.id */
    private Long supplierId;

    /** 用途（按供应商类型自动带出，不手填） */
    private String purpose;

    /** 费用（过程中可填写） */
    private BigDecimal fee;

    /** 关联人用户名 */
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
