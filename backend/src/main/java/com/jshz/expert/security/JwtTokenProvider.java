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
import java.util.Date;

/**
 * JWT 工具（正式版 V1.001）。
 * 签发与校验登录令牌，claims 携带 userId / username / isAdmin / mustChangePassword。
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

    public String createToken(Long userId, String username, boolean isAdmin, boolean mustChangePassword) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("isAdmin", isAdmin)
                .claim("mustChangePassword", mustChangePassword)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
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
