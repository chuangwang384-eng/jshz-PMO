package com.jshz.expert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.PermissionAssignRequest;
import com.jshz.expert.dto.RoleRequest;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.RolePermission;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.RolePermissionMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.OperationLogService;
import com.jshz.expert.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理接口（RBAC 阶段1）。
 * 内置角色（builtin=1）禁删、禁改 code；SUPER_ADMIN 与 GENERAL_MANAGER 权限由代码计算，锁定不可修改绑定。
 * 本接口自身权限由 role:manage 权限码在 permission 数据中配置（勿硬编码）。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResponse<List<Role>> list() {
        return ApiResponse.ok(roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId)));
    }

    @PostMapping
    public ApiResponse<Role> create(@RequestBody RoleRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())) {
            throw new BusinessException("角色编码不能为空");
        }
        String code = request.getCode().trim();
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, code)) > 0) {
            throw new BusinessException("角色编码已存在");
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        role.setBuiltin(0);
        roleMapper.insert(role);
        permissionService.clearCaches();
        return ApiResponse.ok(role);
    }

    @PutMapping("/{id}")
    public ApiResponse<Role> update(@PathVariable Long id, @RequestBody RoleRequest request) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (request == null) {
            return ApiResponse.ok(role);
        }
        boolean builtin = role.getBuiltin() != null && role.getBuiltin() == 1;
        if (builtin && StringUtils.hasText(request.getCode()) && !request.getCode().trim().equals(role.getCode())) {
            throw new BusinessException("内置角色禁止修改编码");
        }
        if (isLocked(role) && request.getStatus() != null && request.getStatus() != 1) {
            throw new BusinessException(role.getCode() + " 角色不可停用");
        }
        if (StringUtils.hasText(request.getCode())) {
            role.setCode(request.getCode().trim());
        }
        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        roleMapper.updateById(role);
        permissionService.clearCaches();
        return ApiResponse.ok(role);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (role.getBuiltin() != null && role.getBuiltin() == 1) {
            throw new BusinessException("内置角色禁止删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        roleMapper.deleteById(id);
        permissionService.clearCaches();
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<String>> rolePermissions(@PathVariable Long id) {
        List<RolePermission> bindings = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        return ApiResponse.ok(bindings.stream().map(RolePermission::getPermissionCode).collect(Collectors.toList()));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id,
                                               @RequestBody PermissionAssignRequest request,
                                               @AuthenticationPrincipal AuthUser authUser,
                                               HttpServletRequest httpRequest) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (isLocked(role)) {
            throw new BusinessException(role.getCode() + " 权限由代码计算，不可修改绑定");
        }
        Set<String> codes = request == null || request.getPermissionCodes() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.getPermissionCodes());
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        for (String code : codes) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id);
            rp.setPermissionCode(code);
            rolePermissionMapper.insert(rp);
        }
        permissionService.clearCaches();
        // 敏感操作日志：角色权限变更
        operationLogService.record(
                authUser == null ? null : authUser.getUserId(),
                authUser == null ? null : authUser.getUsername(),
                OperationLogService.ACTION_ROLE_PERMISSION,
                "调整角色「" + role.getName() + "」权限绑定 " + codes,
                "ROLE", role.getCode(), httpRequest);
        return ApiResponse.ok(null);
    }

    /** 权限由代码计算、不可停用、不可改绑定的内置角色：超级管理员与总经理。 */
    private static boolean isLocked(Role role) {
        return PermissionService.SUPER_ADMIN_CODE.equals(role.getCode())
                || PermissionService.GENERAL_MANAGER_CODE.equals(role.getCode());
    }
}
