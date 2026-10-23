package com.jshz.expert.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色权限分配请求（RBAC 阶段1，覆盖式更新 role_permission）。
 */
@Data
public class PermissionAssignRequest {

    /** 权限码集合（permission.code） */
    private List<String> permissionCodes;
}
