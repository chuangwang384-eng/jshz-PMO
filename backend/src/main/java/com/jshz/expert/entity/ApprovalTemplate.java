package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批申请字段模板实体（对应数据库表 approval_template，需求 R-B4）。
 * 管理员预设常用申请模板（如「联系方式变更」「职称变更」「专家新增」），
 * 员工提交申请时选择模板，表单仅展示模板字段。
 */
@Data
@TableName("approval_template")
public class ApprovalTemplate {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 模板名称（唯一） */
    @JsonProperty("templateName")
    private String templateName;

    /** 模板字段 JSON 数组字符串（中文键，⊆ 专家可编辑白名单 + 照片字段），数据库列 fields */
    @TableField("fields")
    @JsonProperty("fieldsJson")
    private String fieldsJson;

    /** 1 启用 / 0 停用（停用后不可再用于新申请） */
    @JsonProperty("enabled")
    private Integer enabled;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    /** 模板字段解析后的数组（非数据库列，仅响应展示用） */
    @TableField(exist = false)
    @JsonProperty("fields")
    private List<String> fields;
}
