package com.jshz.expert.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-2：JWT tokenVersion claim 单元测试。
 * 纯 Java 测试，不启动 Spring、不连接数据库。
 */
class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(SECRET, 8);
    }

    @Test
    void createAndParseTokenCarriesTokenVersion() {
        JwtTokenProvider provider = provider();
        String token = provider.createToken(9L, "张三", false, false, List.of("EMPLOYEE"), 7L);

        Jws<Claims> jws = provider.parse(token);

        assertThat(jws).isNotNull();
        assertThat(jws.getBody().getSubject()).isEqualTo("张三");
        assertThat(jws.getBody().get("userId", Long.class)).isEqualTo(9L);
        assertThat(provider.extractTokenVersion(jws)).isEqualTo(7L);
    }

    @Test
    void legacyTokenWithoutVersionReturnsNull() {
        JwtTokenProvider provider = provider();
        Date now = new Date();
        String legacy = Jwts.builder()
                .setSubject("李四")
                .claim("userId", 10L)
                .claim("isAdmin", false)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        Jws<Claims> jws = provider.parse(legacy);

        assertThat(jws).isNotNull();
        assertThat(provider.extractTokenVersion(jws)).isNull();
    }

    @Test
    void nullTokenReturnsNullVersion() {
        assertThat(provider().extractTokenVersion(null)).isNull();
    }
}
