package com.jshz.expert.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体（放入 SecurityContext，供鉴权与业务取当前用户）。
 * 角色映射：is_admin=1 -> ROLE_ADMIN；普通员工 -> ROLE_EMPLOYEE。
 */
public class AuthUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final boolean isAdmin;
    private final boolean mustChangePassword;

    public AuthUser(Long userId, String username, boolean isAdmin, boolean mustChangePassword) {
        this.userId = userId;
        this.username = username;
        this.isAdmin = isAdmin;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return isAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
                : List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
