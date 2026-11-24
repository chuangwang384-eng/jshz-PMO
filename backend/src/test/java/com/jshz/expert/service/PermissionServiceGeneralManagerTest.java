package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Permission;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.PermissionMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.RolePermissionMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/** 总经理权限由代码计算：全部权限码减去超管专属码，不依赖 role_permission 绑定。 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceGeneralManagerTest {

    @Mock private PermissionMapper permissionMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @InjectMocks private PermissionService service;

    private static Permission permission(String code) {
        Permission p = new Permission();
        p.setCode(code);
        return p;
    }

    @Test
    void generalManagerGetsEveryCodeExceptSuperAdminOnlyOnes() {
        UserRole binding = new UserRole();
        binding.setUserId(9L);
        binding.setRoleId(54L);
        Role generalManager = new Role();
        generalManager.setId(54L);
        generalManager.setCode(PermissionService.GENERAL_MANAGER_CODE);
        generalManager.setStatus(1);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(binding));
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(generalManager));
        when(permissionMapper.selectList(null)).thenReturn(List.of(
                permission("project:manage"), permission("user:manage"), permission("ai:manage"),
                permission("role:manage"), permission("audit:view"), permission("wecom:sync"),
                permission("storage:manage"), permission("future:new-code")));

        Set<String> permissions = service.getUserPermissions(9L);

        assertThat(permissions).containsExactlyInAnyOrder(
                "project:manage", "user:manage", "ai:manage", "future:new-code")
        ;
        assertThat(permissions).doesNotContain("role:manage", "audit:view", "wecom:sync", "storage:manage");
    }

    @Test
    void excludedCodesStayStableAcrossCacheClears() {
        assertThat(PermissionService.GENERAL_MANAGER_EXCLUDED)
                .containsExactlyInAnyOrder("role:manage", "audit:view", "wecom:sync", "storage:manage");
    }
}
