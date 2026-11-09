package com.jshz.expert.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录用户主体（放入 SecurityContext，供鉴权与业务取当前用户）。
 * RBAC 阶段1：持有 roles（来自 JWT claims）与 permissions（来自 DB + 缓存）；
 * getAuthorities() 返回 ROLE_&lt;角色编码&gt; + 权限码集合；
 * hasPermission(code) 用于业务层权限判断（如审批提交语义）。
 * isAdmin 保留（登录响应 / 敏感字段展示兼容），不再作为鉴权依据。
 */
public class AuthUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final boolean isAdmin;
    private final boolean mustChangePassword;
    private final Set<String> roles;
    private final Set<String> permissions;
    /** 账号是否启用；由 JwtAuthenticationFilter 在每次请求校验后传入真实状态 */
    private final boolean enabled;

    /** 兼容构造：内部服务与测试默认视为启用（过滤器校验通过后才构建正式主体） */
    public AuthUser(Long userId, String username, boolean isAdmin, boolean mustChangePassword,
                    Set<String> roles, Set<String> permissions) {
        this(userId, username, isAdmin, mustChangePassword, roles, permissions, true);
    }

    public AuthUser(Long userId, String username, boolean isAdmin, boolean mustChangePassword,
                    Set<String> roles, Set<String> permissions, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.isAdmin = isAdmin;
        this.mustChangePassword = mustChangePassword;
        this.roles = roles == null ? Set.of() : roles;
        this.permissions = permissions == null ? Set.of() : permissions;
        this.enabled = enabled;
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

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * 业务层权限判断：是否拥有指定权限码。
     */
    public boolean hasPermission(String permissionCode) {
        return permissionCode != null && permissions.contains(permissionCode);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> authorities = new LinkedHashSet<>();
        for (String role : roles) {
            authorities.add("ROLE_" + role);
        }
        authorities.addAll(permissions);
        List<GrantedAuthority> result = new ArrayList<>(authorities.size());
        for (String authority : authorities) {
            result.add(new SimpleGrantedAuthority(authority));
        }
        return result;
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
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
