package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置项模板（内置 5 类 + 管理员可新建模板类型）。
 * 对应数据库表 project_template；template_type 枚举：FILE/EXPERT/SUPPLIER/BUDGET/RICH_TEXT。
 */
@Data
@TableName("project_template")
public class ProjectTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名称 */
    private String name;

    /** 模板类型枚举：FILE/EXPERT/SUPPLIER/BUDGET/RICH_TEXT */
    private String templateType;

    /** 创建人用户名（内置为 SYSTEM） */
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
