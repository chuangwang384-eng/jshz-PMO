package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-权限关联实体（RBAC 阶段1，对应数据库表 role_permission）。
 * 按 permission_code 关联（非 permission.id），配合 UNIQUE(role_id, permission_code)。
 */
@Data
@TableName("role_permission")
public class RolePermission {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 角色ID（role.id） */
    @JsonProperty("roleId")
    private Long roleId;

    /** 权限码（permission.code） */
    @JsonProperty("permissionCode")
    private String permissionCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
