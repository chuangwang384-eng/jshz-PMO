package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户实体（正式版 V1.001）。
 * 对应数据库表 sys_user。
 * 登录账号 = 员工姓名；is_admin 作为管理员名单（替代原 role 表 RBAC 方案）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 = 员工姓名 */
    @JsonProperty("username")
    private String username;

    /** BCrypt 加密密码（禁止明文输出到 JSON） */
    @JsonIgnore
    private String password;

    /** 是否管理员：0 普通员工 / 1 管理员 */
    @JsonProperty("isAdmin")
    private Boolean isAdmin;

    /** 二期企微 userid（预留） */
    @JsonProperty("wecomUserId")
    @com.baomidou.mybatisplus.annotation.TableField("wecom_userid")
    private String wecomUserId;

    /** 是否需强制改密：1是 0否（首次登录引导改密） */
    @JsonProperty("mustChangePassword")
    private Boolean mustChangePassword;

    /** 状态：1 启用 / 0 停用 */
    @JsonProperty("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    /** RBAC 阶段1：用户拥有的角色名称（非表字段，查询时填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @JsonProperty("roles")
    private List<String> roles;

    /** RBAC 阶段1：用户拥有的角色 ID（非表字段，查询时填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @JsonProperty("roleIds")
    private List<Long> roleIds;
}
