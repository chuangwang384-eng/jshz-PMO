package com.jshz.expert.dto;

import lombok.Data;

/**
 * 用户管理请求（新建 / 修改用户，管理员专用）。
 * 新建时密码默认 111111（BCrypt），无需传 password。
 */
@Data
public class UserRequest {

    /** 登录账号 = 员工姓名 */
    private String username;

    /** 是否管理员：0 普通员工 / 1 管理员 */
    private Boolean isAdmin;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
