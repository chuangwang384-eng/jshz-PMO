package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户档案实体。
 * 原知识星球"客户喜好"子板块改造为独立客户信息模块（客户档案管理）。
 */
@Data
@TableName("customer")
public class Customer {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户姓名 */
    private String name;

    /** 工作单位 */
    private String workUnit;

    /** 工作职位 */
    private String workPosition;

    /** 工作职级 */
    private String workLevel;

    /** 现任职务 */
    private String currentDuty;

    /** 客户喜好（文本） */
    private String preferences;

    /** 备注 */
    private String remark;

    /** 软删除标记：0正常 1已删除（默认隐藏，可恢复） */
    private Integer deleted;

    /** 删除人 */
    private String deleteBy;

    /** 删除时间 */
    private LocalDateTime deleteTime;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最近更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
