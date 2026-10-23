package com.jshz.expert.dto;

import lombok.Data;

/**
 * 角色创建/更新请求（RBAC 阶段1）。
 */
@Data
public class RoleRequest {

    /** 角色编码（唯一；内置角色禁改） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
