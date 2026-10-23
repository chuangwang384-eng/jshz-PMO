package com.jshz.expert.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.service.PermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据库权限过滤器（RBAC 阶段1，置于 JwtAuthenticationFilter 之后）。
 * 规则来源：permission 表 type=2 的接口行（method + Ant path -> code）。
 * 流程：
 * 1. 未登录请求直接放行（由 SecurityConfig authenticated 规则统一返回 401）；
 * 2. 命中任一规则（method+path 匹配）→ 校验当前用户权限码集合；无该码返回 403；
 * 3. 未命中任何规则 → 放行（登录即可）。
 * SecurityConfig 不再硬编码 hasRole("ADMIN")，原 10 条硬编码规则已迁移为 permission 数据。
 */
@Component
@RequiredArgsConstructor
public class DbPermissionFilter extends OncePerRequestFilter {

    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            filterChain.doFilter(request, response);
            return;
        }
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();
        if (path == null || path.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        List<PermissionService.PermissionRule> rules = permissionService.getPermissionRules();
        for (PermissionService.PermissionRule rule : rules) {
            if (rule.method().equals(method) && antPathMatcher.match(rule.path(), path)) {
                if (!authUser.hasPermission(rule.code())) {
                    writeForbidden(response);
                    return;
                }
                // 命中且拥有权限码，放行
                break;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(403, "无权限执行该操作")));
    }
}
