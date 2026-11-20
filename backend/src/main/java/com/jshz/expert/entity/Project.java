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

    /** 项目名称（会议名称，必填） */
    private String name;

    /** 大类：ONLINE 线上 / OFFLINE 线下（前端展示名「地点」，2026-11-20 起改称，字段保留） */
    private String category;

    /** 项目类型：PLATFORM 平台类 / DIRECT_PAY 直付类 / KEY 重点类（必填，2026-11-20 新增） */
    private String projectType;

    /** 团队：DEPT1 项目一部 / DEPT2 项目二部 / DEPT3 项目三部（必填，服务层校验） */
    private String team;

    /** 会议城市 supplier_city.id（非必填，复用供应商城市字典） */
    private Long cityId;

    /** 会议举办开始日期（可单日可多日；单日=与结束同日） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate meetingStartDate;

    /** 会议举办结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate meetingEndDate;

    /** 客户单位（非必填） */
    private String clientCompany;

    /** 客户姓名（非必填） */
    private String clientName;

    /** 备注（非必填） */
    private String remark;

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
