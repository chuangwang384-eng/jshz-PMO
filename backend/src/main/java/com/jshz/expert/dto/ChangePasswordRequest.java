package com.jshz.expert.dto;

import lombok.Data;

/**
 * 修改密码请求（初始密码强制改密接口）。
 */
@Data
public class ChangePasswordRequest {

    private String oldPassword;

    private String newPassword;
}
