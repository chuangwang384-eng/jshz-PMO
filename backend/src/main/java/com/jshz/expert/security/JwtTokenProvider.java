package com.jshz.expert.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具（正式版 V1.001，RBAC 阶段1）。
 * 签发与校验登录令牌，claims 携带 userId / username / isAdmin / mustChangePassword / roles。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expireMillis;

    public JwtTokenProvider(@Value("${expert.jwt.secret}") String secret,
                            @Value("${expert.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String createToken(Long userId, String username, boolean isAdmin, boolean mustChangePassword,
                              List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("isAdmin", isAdmin)
                .claim("mustChangePassword", mustChangePassword)
                .claim("roles", roles == null ? new ArrayList<>() : roles)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token 中的角色编码列表；缺失或非法返回空列表（兼容旧 token，权限由 DB 兜底）。
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Jws<Claims> jws) {
        if (jws == null) {
            return new ArrayList<>();
        }
        Object roles = jws.getBody().get("roles");
        if (roles instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * JWT 有效期（秒），用于登录 cookie 的 Max-Age 保持同步。
     */
    public long getExpireSeconds() {
        return expireMillis / 1000L;
    }

    /**
     * 解析并校验 token，非法/过期返回 null。
     */
    public Jws<Claims> parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        } catch (Exception e) {
            return null;
        }
    }
}
