package com.jshz.expert.security;

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

/**
 * JWT 认证过滤器。
 * 从 Authorization: Bearer <token> 或 cookie expert_token 解析登录态；
 * 校验通过则写入 SecurityContext；校验失败不设置（由安全配置统一返回 401）。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

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
                    AuthUser authUser = new AuthUser(
                            userId,
                            username,
                            Boolean.TRUE.equals(isAdmin),
                            Boolean.TRUE.equals(mustChangePassword));
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
