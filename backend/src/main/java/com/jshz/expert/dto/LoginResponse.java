package com.jshz.expert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 登录成功响应（RBAC 阶段1：token + 用户信息 + roles + permissions + 强制改密标记）。
 * 保留 isAdmin 字段兼容前端既有判断。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String username;

    private Boolean isAdmin;

    private Boolean mustChangePassword;

    /** 角色编码列表 */
    private List<String> roles;

    /** 权限码集合 */
    private List<String> permissions;

    /** 兼容旧构造（roles / permissions 为空列表） */
    public LoginResponse(String token, String username, Boolean isAdmin, Boolean mustChangePassword) {
        this(token, username, isAdmin, mustChangePassword, List.of(), List.of());
    }
}
