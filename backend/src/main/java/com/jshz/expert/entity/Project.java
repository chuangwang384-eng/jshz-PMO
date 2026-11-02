package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目主表（项目管理模块）。
 * 对应数据库表 project。
 * 软删：status=DELETED 列表隐藏，数据保留；已取消 CANCELED 为特殊终态。
 */
@Data
@TableName("project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称（必填） */
    private String name;

    /** 大类：ONLINE 线上 / OFFLINE 线下（固定） */
    private String category;

    /** 类型 project_type.id */
    private Long typeId;

    /** 计划开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 计划结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 负责人 sys_user.id（限定拥有 PROJECT_OWNER 角色用户；立项必填） */
    private Long ownerId;

    /** 创建人 sys_user.id */
    private Long creatorId;

    /** 当前阶段 project_phase.id（项目状态） */
    private Long phaseId;

    /** 状态：ACTIVE 正常 / CANCELED 已取消 / DELETED 已软删 */
    private String status;

    /** 取消原因（取消必填） */
    private String cancelReason;

    /** 取消时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    /** 预算金额（BUDGET 模板登记，项目级全局一份） */
    private BigDecimal budgetAmount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
