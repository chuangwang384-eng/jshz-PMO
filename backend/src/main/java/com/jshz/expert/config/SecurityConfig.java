package com.jshz.expert.config;

import com.jshz.expert.security.DbPermissionFilter;
import com.jshz.expert.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置（正式版 V1.001，RBAC 阶段1）。
 * 白名单：健康检查 / 登录登出 / 企微登录回调；其余接口登录即可。
 * 原 ADMIN 硬编码规则已迁移为 permission 数据，由 DbPermissionFilter 按 method+path 拦截。
 * 静态照片资源 /api/files/** 需登录后访问。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DbPermissionFilter dbPermissionFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期")))
                .authorizeHttpRequests(auth -> auth
                        // 白名单（无需登录）：健康检查 / 登录登出 / 企微登录回调
                        .requestMatchers("/health", "/ready").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/wecom/**").permitAll()
                        // RBAC 阶段1：原 10 条 hasRole("ADMIN") 硬编码规则已全部迁移为 permission 数据，
                        // 由 DbPermissionFilter（置于 JwtAuthenticationFilter 之后）按 method+path 匹配权限码拦截；
                        // 其余接口登录即可。
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(dbPermissionFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS：内网固定前端来源（192.168.0.95:5173 / localhost:5173），
     * 配合 allowCredentials(true) 不得使用 *。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://192.168.0.95:5173", "http://localhost:5173", "http://192.168.0.144:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
