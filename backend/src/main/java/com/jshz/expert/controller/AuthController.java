package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.dto.ChangePasswordRequest;
import com.jshz.expert.dto.LoginRequest;
import com.jshz.expert.dto.LoginResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.security.JwtTokenProvider;
import com.jshz.expert.service.AuthService;
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
 * 认证接口（正式版 V1.001）。
 * /api/auth/login 账号=员工姓名登录、/api/auth/me、强制改密；企微登录为占位（二期）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 账号密码登录：账号 = 员工姓名，BCrypt 校验，签发 JWT。
     * JWT 写入 httpOnly cookie（内网 http 不设 Secure；SameSite=Lax；Path=/）。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request,
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
        return ApiResponse.ok(loginResponse);
    }

    /**
     * 登出：清除登录 cookie（白名单放行，token 失效后仍可清理）。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("expert_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
                                            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authUser, request);
        return ApiResponse.ok(null);
    }

    /**
     * 企业微信登录配置（占位，二期实现）。
     */
    @GetMapping("/wecom/config")
    public ApiResponse<Map<String, Object>> wecomConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", false);
        data.put("message", "企业微信登录为二期功能，当前请使用账号密码登录");
        return ApiResponse.ok(data);
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
