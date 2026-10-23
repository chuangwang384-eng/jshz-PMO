package com.jshz.expert.security;

import com.jshz.expert.service.PermissionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT 认证过滤器（RBAC 阶段1）。
 * 从 Authorization: Bearer <token> 或 cookie expert_token 解析登录态；
 * 解析 roles 并实时加载用户权限码集合（带缓存），构造 AuthUser 写入 SecurityContext。
 * 旧 token 缺失 roles 时按 isAdmin 兼容回退；权限码始终从 DB + 缓存获取，保证权限变更即时生效。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final PermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            Jws<Claims> jws = jwtTokenProvider.parse(token);
            if (jws != null) {
                Claims claims = jws.getBody();
                Long userId = claims.get("userId", Long.class);
                String username = claims.getSubject();
                Boolean isAdmin = claims.get("isAdmin", Boolean.class);
                Boolean mustChangePassword = claims.get("mustChangePassword", Boolean.class);
                if (username != null) {
                    List<String> tokenRoles = jwtTokenProvider.extractRoles(jws);
                    Set<String> roles = new LinkedHashSet<>(tokenRoles);
                    if (roles.isEmpty()) {
                        // 兼容旧 token：按 isAdmin 回退角色编码
                        if (Boolean.TRUE.equals(isAdmin)) {
                            roles.add("ADMIN");
                        } else {
                            roles.add("EMPLOYEE");
                        }
                    }
                    // 权限码始终从 DB + 缓存加载（SUPER_ADMIN 代码内全权；角色绑定变更后清缓存即时生效）
                    Set<String> permissions = permissionService.getUserPermissions(userId);
                    AuthUser authUser = new AuthUser(
                            userId,
                            username,
                            Boolean.TRUE.equals(isAdmin),
                            Boolean.TRUE.equals(mustChangePassword),
                            roles,
                            permissions);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 兼容 cookie 方式（内网前端可选）
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if ("expert_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
