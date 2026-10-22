package com.jshz.expert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功响应（设计文档 5.1：token + 用户信息 + 强制改密标记）。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String username;

    private Boolean isAdmin;

    private Boolean mustChangePassword;
}
