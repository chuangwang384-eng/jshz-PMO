package com.jshz.expert.dto;

import lombok.Data;

/**
 * 登录请求（设计文档：账号 = 员工姓名，密码 BCrypt 校验）。
 */
@Data
public class LoginRequest {

    private String username;

    private String password;
}
