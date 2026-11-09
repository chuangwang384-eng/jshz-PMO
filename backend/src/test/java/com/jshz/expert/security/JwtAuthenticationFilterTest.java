package com.jshz.expert.security;

import com.jshz.expert.service.PermissionService;
import com.jshz.expert.service.UserAuthStateService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * P1-2：JWT 过滤器登录态校验单元测试。
 * 覆盖：正常放行、用户停用、token_version 不一致、存量 Token 无 tv。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PermissionService permissionService;
    @Mock
    private UserAuthStateService userAuthStateService;
    @Mock
    private Jws<Claims> jws;
    @Mock
    private Claims claims;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, permissionService, userAuthStateService);
        when(jwtTokenProvider.parse("token")).thenReturn(jws);
        when(jws.getBody()).thenReturn(claims);
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(claims.getSubject()).thenReturn("alice");
        when(claims.get("isAdmin", Boolean.class)).thenReturn(false);
        when(claims.get("mustChangePassword", Boolean.class)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void doFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void validTokenEstablishesAuthentication() throws Exception {
        when(jwtTokenProvider.extractRoles(jws)).thenReturn(List.of("EMPLOYEE"));
        when(permissionService.getUserPermissions(1L)).thenReturn(Set.of("project:view"));
        when(jwtTokenProvider.extractTokenVersion(jws)).thenReturn(2L);
        when(userAuthStateService.get(1L))
                .thenReturn(new UserAuthStateService.UserAuthState(true, 1, 2L));

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
    }

    @Test
    void disabledUserIsNotAuthenticated() throws Exception {
        when(jwtTokenProvider.extractTokenVersion(jws)).thenReturn(2L);
        when(userAuthStateService.get(1L))
                .thenReturn(new UserAuthStateService.UserAuthState(true, 0, 2L));

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void versionMismatchIsNotAuthenticated() throws Exception {
        when(jwtTokenProvider.extractTokenVersion(jws)).thenReturn(1L);
        when(userAuthStateService.get(1L))
                .thenReturn(new UserAuthStateService.UserAuthState(true, 1, 2L));

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void legacyTokenWithoutTvIsNotAuthenticated() throws Exception {
        when(jwtTokenProvider.extractTokenVersion(jws)).thenReturn(null);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
