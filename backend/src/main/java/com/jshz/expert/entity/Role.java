package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体（RBAC 阶段1，对应数据库表 role）。
 * 内置角色 builtin=1：禁删、禁改 code；SUPER_ADMIN 锁定不可改。
 */
@Data
@TableName("role")
public class Role {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 角色编码（唯一，如 SUPER_ADMIN / ADMIN / APPROVER / EMPLOYEE） */
    @JsonProperty("code")
    private String code;

    /** 角色名称 */
    @JsonProperty("name")
    private String name;

    /** 角色描述 */
    @JsonProperty("description")
    private String description;

    /** 状态：1 启用 / 0 停用 */
    @JsonProperty("status")
    private Integer status;

    /** 是否内置：1 内置（禁删禁改 code；SUPER_ADMIN 锁定） */
    @JsonProperty("builtin")
    private Integer builtin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;
}
