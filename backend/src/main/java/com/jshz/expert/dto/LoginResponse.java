package com.jshz.expert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
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

    /** 用户ID（个人中心信息卡展示） */
    private Long id;

    /** 注册时间（个人中心信息卡展示） */
    private LocalDateTime createTime;

    /** 角色友好名称列表（SUPER_ADMIN→超级管理员、ADMIN→管理员、APPROVER→审批人、EMPLOYEE→普通员工） */
    private List<String> roleNames;

    /** 兼容旧构造（roles / permissions 为空列表，id / createTime 为 null，roleNames 为空列表） */
    public LoginResponse(String token, String username, Boolean isAdmin, Boolean mustChangePassword) {
        this(token, username, isAdmin, mustChangePassword, List.of(), List.of(), null, null, List.of());
    }
}
