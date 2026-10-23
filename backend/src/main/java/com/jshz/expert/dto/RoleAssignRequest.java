package com.jshz.expert.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户角色分配请求（RBAC 阶段1，覆盖式更新 user_role）。
 */
@Data
public class RoleAssignRequest {

    /** 角色ID集合 */
    private List<Long> roleIds;
}
