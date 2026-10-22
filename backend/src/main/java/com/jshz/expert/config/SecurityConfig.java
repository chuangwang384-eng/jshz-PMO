package com.jshz.expert.config;

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
 * 安全配置（正式版 V1.001）。
 * 白名单：登录 / 企微占位 / 健康检查；管理写接口仅 ADMIN；
 * 静态照片资源 /api/files/** 需登录后访问。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
                        // 白名单（无需登录）
                        .requestMatchers("/health", "/ready").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/wecom/**").permitAll()
                        // 管理员专属：专家写接口 / 导入模板 / 上传 / 用户管理 / 审计
                        .requestMatchers(HttpMethod.POST, "/api/experts", "/api/experts/import").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/experts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/experts/**").hasRole("ADMIN")
                        .requestMatchers("/api/experts/template").hasRole("ADMIN")
                        // 全量下载收紧为仅管理员（勾选下载 selected/* 保持登录可用）
                        .requestMatchers(HttpMethod.GET, "/api/experts/export", "/api/experts/export/employee").hasRole("ADMIN")
                        .requestMatchers("/api/upload/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")
                        // 审批流：配置更新 / 申请列表 / 审批动作仅 ADMIN，其余 /api/approval/** 登录即可
                        .requestMatchers(HttpMethod.PUT, "/api/approval/config").hasRole("ADMIN")
                        .requestMatchers("/api/approval/requests/{id}/approve", "/api/approval/requests/{id}/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/approval/requests").hasRole("ADMIN")
                        .requestMatchers("/api/export/full").hasRole("ADMIN")
                        .requestMatchers("/api/experts/export/full").hasRole("ADMIN")
                        // 其余接口：登录即可（员工可下载全量，下载留痕）
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
