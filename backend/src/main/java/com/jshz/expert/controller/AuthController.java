package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.config.WecomProperties;
import com.jshz.expert.dto.ChangePasswordRequest;
import com.jshz.expert.dto.LoginRequest;
import com.jshz.expert.dto.LoginResponse;
import com.jshz.expert.dto.WecomConfigVo;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.security.JwtTokenProvider;
import com.jshz.expert.service.AuthService;
import com.jshz.expert.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口（正式版 V1.001；2026-10-27 企微账号体系改造）。
 * /api/auth/login 登录标识三模式（NAME/COMPAT/MOBILE）、/api/auth/me、强制改密；
 * /api/auth/wecom/config 返回登录模式供前端自适应；企微 OAuth 登录仍为占位（等备案）。
 * 登录 / 登出 / 修改密码埋点写入 operation_log（用户操作日志改版）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final OperationLogService operationLogService;
    private final WecomProperties wecomProperties;

    /**
     * 账号密码登录：账号 = 员工姓名，BCrypt 校验，签发 JWT。
     * JWT 写入 httpOnly cookie（内网 http 不设 Secure；SameSite=Lax；Path=/）。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);
        ResponseCookie cookie = ResponseCookie.from("expert_token", loginResponse.getToken())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtTokenProvider.getExpireSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        // token 已写入 cookie，响应体不再回传，避免前端落入 localStorage
        loginResponse.setToken(null);
        // 敏感操作日志：登录成功
        operationLogService.record(null, loginResponse.getUsername(), OperationLogService.ACTION_LOGIN,
                "登录成功", "USER", loginResponse.getUsername(), httpRequest);
        return ApiResponse.ok(loginResponse);
    }

    /**
     * 登出：清除登录 cookie（白名单放行，token 失效后仍可清理）。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthUser authUser,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("expert_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        // 敏感操作日志：登出（token 已失效时无登录态，跳过记录）
        if (authUser != null) {
            operationLogService.record(authUser.getUserId(), authUser.getUsername(),
                    OperationLogService.ACTION_LOGOUT, "退出登录", "USER", authUser.getUsername(), httpRequest);
        }
        return ApiResponse.ok(null);
    }

    /**
     * 当前登录用户信息。
     */
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(authService.me(authUser));
    }

    /**
     * 修改密码（初始密码 111111 首次登录后强制改密）。
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        authService.changePassword(authUser, request);
        // 敏感操作日志：修改本人密码
        operationLogService.record(authUser.getUserId(), authUser.getUsername(),
                OperationLogService.ACTION_CHANGE_PASSWORD, "修改本人密码", "USER", authUser.getUsername(),
                httpRequest);
        return ApiResponse.ok(null);
    }

    /**
     * 登录页自适应配置：返回登录标识模式（NAME/COMPAT/MOBILE，与 AuthService 归一化逻辑一致）
     * 供前端切换输入框文案；企微 OAuth 登录仍为占位（等备案），enabled 恒为 false。
     */
    @GetMapping("/wecom/config")
    public ApiResponse<WecomConfigVo> wecomConfig() {
        String mode = wecomProperties.getLoginMode();
        mode = mode == null ? "NAME" : mode.trim().toUpperCase();
        String message;
        switch (mode) {
            case "COMPAT":
                message = "已绑定企微用户请使用手机号登录；未绑定用户可输入姓名 + 初始密码 111111 自动注册";
                break;
            case "MOBILE":
                message = "请使用企微通讯录同步的手机号 + 密码登录；未绑定请联系管理员";
                break;
            default:
                message = "当前为姓名登录模式：输入姓名 + 初始密码 111111 即可自动注册并登录";
                break;
        }
        return ApiResponse.ok(new WecomConfigVo(false, mode, message));
    }

    /**
     * 企业微信登录入口（占位，二期实现）。
     */
    @GetMapping("/wecom/login")
    public ApiResponse<Map<String, Object>> wecomLogin() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", false);
        data.put("message", "企业微信登录为二期功能，当前请使用账号密码登录");
        return ApiResponse.ok(data);
    }

    /**
     * 企业微信回调（占位，二期实现）。
     */
    @GetMapping("/wecom/callback")
    public ApiResponse<Map<String, Object>> wecomCallback() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", false);
        data.put("message", "企业微信登录为二期功能，当前请使用账号密码登录");
        return ApiResponse.ok(data);
    }
}
