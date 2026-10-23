package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.entity.Permission;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.RolePermission;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.PermissionMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.RolePermissionMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 权限服务（RBAC 阶段1）。
 * 职责：
 * 1. 权限规则缓存：permission 表中 method+path -> code 的 Ant 匹配规则集合；
 * 2. 用户权限码集合缓存：user_role -> role_permission -> permission 并集；
 * 3. 角色/权限写操作后立即清缓存（clearCaches），保证新增角色/权限无需改代码即可生效。
 * SUPER_ADMIN 角色代码内强制全权（不依赖 role_permission 绑定，防止误改导致权限失效）。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 超级管理员角色编码：代码内强制全权 */
    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    /** 权限规则（method+path -> code），来自 permission 表 type=2 的接口行 */
    public record PermissionRule(String method, String path, String code) {
    }

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    /** 接口权限规则缓存 */
    private volatile List<PermissionRule> ruleCache = new ArrayList<>();

    /** 用户 -> 权限码集合缓存 */
    private final Map<Long, Set<String>> userPermissionsCache = new ConcurrentHashMap<>();

    /** 用户 -> 角色编码集合缓存 */
    private final Map<Long, Set<String>> userRolesCache = new ConcurrentHashMap<>();

    /**
     * 加载全部接口权限规则（type=2 且 method/path 非空）。
     */
    public List<PermissionRule> getPermissionRules() {
        List<PermissionRule> rules = ruleCache;
        if (rules.isEmpty()) {
            synchronized (this) {
                rules = ruleCache;
                if (rules.isEmpty()) {
                    List<Permission> permissions = permissionMapper.selectList(
                            new LambdaQueryWrapper<Permission>()
                                    .eq(Permission::getType, 2)
                                    .isNotNull(Permission::getMethod)
                                    .isNotNull(Permission::getPath));
                    List<PermissionRule> built = new ArrayList<>(permissions.size());
                    for (Permission p : permissions) {
                        if (p.getMethod() != null && p.getPath() != null) {
                            built.add(new PermissionRule(p.getMethod().toUpperCase(), p.getPath(), p.getCode()));
                        }
                    }
                    ruleCache = built;
                    rules = built;
                }
            }
        }
        return rules;
    }

    /**
     * 用户角色编码集合（启用角色），带缓存。
     */
    public Set<String> getUserRoles(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> cached = userRolesCache.get(userId);
        if (cached != null) {
            return cached;
        }
        Set<String> roles = loadUserRoles(userId);
        userRolesCache.put(userId, roles);
        return roles;
    }

    /**
     * 用户权限码集合（启用角色绑定 + SUPER_ADMIN 全权），带缓存。
     */
    public Set<String> getUserPermissions(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> cached = userPermissionsCache.get(userId);
        if (cached != null) {
            return cached;
        }
        Set<String> permissions = loadUserPermissions(userId);
        userPermissionsCache.put(userId, permissions);
        return permissions;
    }

    /**
     * 判断用户是否拥有指定权限码。
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        return permissionCode != null && getUserPermissions(userId).contains(permissionCode);
    }

    /**
     * 清空全部缓存（角色/权限/用户角色写操作后调用，立即生效）。
     */
    public void clearCaches() {
        synchronized (this) {
            ruleCache = new ArrayList<>();
        }
        userPermissionsCache.clear();
        userRolesCache.clear();
    }

    // ---------------- 私有加载 ----------------

    private Set<String> loadUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        List<Role> roles = roleMapper.selectBatchIds(roleIds).stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                .collect(Collectors.toList());
        return roles.stream().map(Role::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> loadUserPermissions(Long userId) {
        Set<String> roles = loadUserRoles(userId);
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }
        // SUPER_ADMIN 代码内强制全权：返回全部权限码（菜单+接口）
        if (roles.contains(SUPER_ADMIN_CODE)) {
            List<Permission> all = permissionMapper.selectList(null);
            return all.stream().map(Permission::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        // 过滤停用角色
        Set<Long> activeRoleIds = roleMapper.selectBatchIds(roleIds).stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                .map(Role::getId)
                .collect(Collectors.toSet());
        if (activeRoleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<RolePermission> bindings = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, activeRoleIds));
        Set<String> codes = bindings.stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            return Collections.emptySet();
        }
        // 只保留 permission 表中真实存在的码（防脏数据）
        List<Permission> valid = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().in(Permission::getCode, codes));
        return valid.stream().map(Permission::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
