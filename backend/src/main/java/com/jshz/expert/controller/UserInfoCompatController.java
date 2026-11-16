package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.dto.LoginResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧客户端兼容接口（2026-11-06）。
 * 早期前端缓存在登录后调用 GET /api/user/info 读取当前用户；该接口早已下线，
 * 旧缓存页面会因此在登录后报 500 / 白屏。此处按 /api/auth/me 同结构返回，
 * 使未刷新的旧页面也能正常完成登录流程，无需用户手动清缓存。
 * 仅为兼容保留，新前端不再调用；登录即可（未命中 permission 规则即放行）。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInfoCompatController {

    private final AuthService authService;

    @GetMapping("/info")
    public ApiResponse<LoginResponse> info(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(authService.me(authUser));
    }
}
