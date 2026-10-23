package com.jshz.expert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.entity.Permission;
import com.jshz.expert.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限点查询接口（RBAC 阶段1）。
 * 按菜单（type=1）与接口（type=2）分组返回，供角色分配界面使用。
 * 本接口自身权限由 role:manage / user:manage 权限码在 permission 数据中配置（勿硬编码）。
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionMapper permissionMapper;

    @GetMapping
    public ApiResponse<Map<String, List<Permission>>> list() {
        List<Permission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getId));
        Map<String, List<Permission>> grouped = new LinkedHashMap<>();
        grouped.put("menus", all.stream()
                .filter(p -> p.getType() != null && p.getType() == 1)
                .collect(Collectors.toList()));
        grouped.put("apis", all.stream()
                .filter(p -> p.getType() != null && p.getType() == 2)
                .collect(Collectors.toList()));
        return ApiResponse.ok(grouped);
    }
}
