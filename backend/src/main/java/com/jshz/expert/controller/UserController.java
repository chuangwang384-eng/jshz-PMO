package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.dto.RoleAssignRequest;
import com.jshz.expert.dto.UserRequest;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理接口（正式版 V1.001，管理员名单管理，仅 ADMIN）。
 * 新建 / 修改 / 停用 / 删除 / 重置密码（重置为 111111）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<SysUser>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        List<SysUser> all = userService.listUsers(keyword).stream()
                .peek(u -> u.setPassword(null))
                .collect(Collectors.toList());
        int from = (int) Math.min((page - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        List<SysUser> items = all.subList(from, to);
        return ApiResponse.ok(PageResult.of(all.size(), items));
    }

    @PostMapping
    public ApiResponse<SysUser> create(@RequestBody UserRequest request) {
        SysUser user = userService.createUser(request);
        user.setPassword(null);
        return ApiResponse.ok(user);
    }

    @PutMapping("/{id}")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody UserRequest request) {
        SysUser user = userService.updateUser(id, request);
        user.setPassword(null);
        return ApiResponse.ok(user);
    }

    /**
     * 重置密码为 111111（强制改密标记置真）。
     */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok(null);
    }

    /**
     * 为用户分配角色（覆盖式，RBAC 阶段1）。
     * 权限由 user:manage 权限码在 permission 数据中配置。
     */
    @PutMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody RoleAssignRequest request) {
        userService.assignRoles(id, request == null ? null : request.getRoleIds());
        return ApiResponse.ok(null);
    }
}
