package com.jshz.expert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录页自适应配置（GET /api/auth/wecom/config）。
 * 前端据此切换输入框文案，不写死模式。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WecomConfigVo {
    /** 企微登录是否启用（本期仅通讯录同步，登录仍为账号密码，故恒为 false） */
    private boolean enabled;
    /** 登录标识模式：NAME / COMPAT / MOBILE */
    private String loginMode;
    private String message;
}
