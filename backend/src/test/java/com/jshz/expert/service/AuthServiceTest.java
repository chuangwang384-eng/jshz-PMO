package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.config.WecomProperties;
import com.jshz.expert.dto.ChangePasswordRequest;
import com.jshz.expert.dto.LoginResponse;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 角色名称中文化改造（动态读取 role 表 name）单元测试。
 * <p>纯 Mockito 单测：不启动 Spring 上下文、不连数据库、不触碰任何生产数据。
 * 覆盖：六种内置角色名称一致性、不再显示英文编码、角色顺序保持、动态生效、
 * 新增角色免改代码、空角色列表、缺记录回退、空名称回退、缺项不错位、查询异常不静默伪装。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PermissionService permissionService;
    @Mock
    private UserAuthStateService userAuthStateService;
    @Mock
    private UserService userService;
    @Mock
    private WecomProperties wecomProperties;

    @InjectMocks
    private AuthService authService;

    private Role role(String code, String name) {
        Role r = new Role();
        r.setCode(code);
        r.setName(name);
        return r;
    }

    /** 六种内置角色，name 与数据库 role 表当前值一致 */
    private List<Role> sixBuiltinRoles() {
        return List.of(
                role("SUPER_ADMIN", "超级管理员"),
                role("ADMIN", "管理员"),
                role("APPROVER", "审批人"),
                role("EMPLOYEE", "普通员工"),
                role("KNOWLEDGE_MAINTAINER", "知识星球维护人"),
                role("PROJECT_OWNER", "项目负责人"));
    }

    @Test
    void builtinRoleNamesMatchDatabaseAndNoEnglishCode() {
        when(roleMapper.selectByCodes(any())).thenReturn(sixBuiltinRoles());

        List<String> roles = List.of("SUPER_ADMIN", "ADMIN", "APPROVER", "EMPLOYEE",
                "KNOWLEDGE_MAINTAINER", "PROJECT_OWNER");

        assertThat(authService.toRoleNames(roles)).containsExactly(
                "超级管理员", "管理员", "审批人", "普通员工", "知识星球维护人", "项目负责人");
    }

    @Test
    void roleNamesKeepOriginalOrder() {
        when(roleMapper.selectByCodes(any())).thenReturn(sixBuiltinRoles());

        List<String> roles = List.of("PROJECT_OWNER", "EMPLOYEE", "SUPER_ADMIN", "APPROVER");

        assertThat(authService.toRoleNames(roles)).containsExactly(
                "项目负责人", "普通员工", "超级管理员", "审批人");
    }

    @Test
    void changedNameReflectsOnNextCall() {
        // 第一次：旧名称
        when(roleMapper.selectByCodes(any())).thenReturn(List.of(role("ADMIN", "管理员")));
        assertThat(authService.toRoleNames(List.of("ADMIN"))).containsExactly("管理员");

        // 数据库 role.name 被修改后，重新请求返回新名称（动态读取，无需改代码）
        when(roleMapper.selectByCodes(any())).thenReturn(List.of(role("ADMIN", "系统管理员")));
        assertThat(authService.toRoleNames(List.of("ADMIN"))).containsExactly("系统管理员");
    }

    @Test
    void newRoleReturnsDatabaseNameWithoutCodeChange() {
        // 库内新增角色，映射代码无需改动即可返回数据库名称
        when(roleMapper.selectByCodes(any())).thenReturn(List.of(role("NEW_ROLE", "新角色")));

        assertThat(authService.toRoleNames(List.of("NEW_ROLE"))).containsExactly("新角色");
    }

    @Test
    void emptyRolesReturnsEmptyAndSkipsQuery() {
        assertThat(authService.toRoleNames(List.of())).isEmpty();
        assertThat(authService.toRoleNames(null)).isEmpty();
        verify(roleMapper, never()).selectByCodes(any());
    }

    @Test
    void missingCodeFallsBackToCode() {
        // 查询成功但某编码无记录：回退为该编码本身，不导致列表错位
        when(roleMapper.selectByCodes(any())).thenReturn(List.of(
                role("ADMIN", "管理员"), role("EMPLOYEE", "普通员工")));

        assertThat(authService.toRoleNames(List.of("ADMIN", "GHOST_ROLE", "EMPLOYEE")))
                .containsExactly("管理员", "GHOST_ROLE", "普通员工");
    }

    @Test
    void blankNameFallsBackToCodeNotEmptyString() {
        // name 为空（null / 空串）：不得返回空字符串作为正常展示名称
        when(roleMapper.selectByCodes(any())).thenReturn(List.of(
                role("ADMIN", ""), role("APPROVER", null)));

        List<String> names = authService.toRoleNames(List.of("ADMIN", "APPROVER"));
        assertThat(names).containsExactly("ADMIN", "APPROVER");
        assertThat(names).doesNotContain("");
    }

    @Test
    void partialMissingKeepsPositionAndNoNpe() {
        // 乱序 + 部分缺失 + 空名称混合：不错位、不空指针
        when(roleMapper.selectByCodes(any())).thenReturn(List.of(
                role("PROJECT_OWNER", "项目负责人"), role("ADMIN", null)));

        assertThat(authService.toRoleNames(List.of("PROJECT_OWNER", "MISSING", "ADMIN", "SUPER_ADMIN")))
                .containsExactly("项目负责人", "MISSING", "ADMIN", "SUPER_ADMIN");
    }

    @Test
    void queryFailureThrowsBusinessExceptionNotSilent() {
        // 数据库查询异常：不得静默返回原编码伪装正常，按业务异常流程上报
        when(roleMapper.selectByCodes(any()))
                .thenThrow(new DataAccessResourceFailureException("connection refused (simulated)"));

        assertThatThrownBy(() -> authService.toRoleNames(List.of("ADMIN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("获取角色名称失败");
    }

    @Test
    void changePasswordBumpsVersionAndIssuesNewToken() {
        SysUser before = new SysUser();
        before.setId(1L);
        before.setUsername("alice");
        before.setPassword("old-hash");
        before.setIsAdmin(false);
        before.setMustChangePassword(true);
        before.setTokenVersion(3);

        SysUser after = new SysUser();
        after.setId(1L);
        after.setUsername("alice");
        after.setPassword("new-hash");
        after.setIsAdmin(false);
        after.setMustChangePassword(false);
        after.setTokenVersion(4);

        when(sysUserMapper.selectById(1L)).thenReturn(before, after);
        when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");
        when(permissionService.getUserRoles(1L)).thenReturn(Set.of("EMPLOYEE"));
        when(permissionService.getUserPermissions(1L)).thenReturn(Set.of("project:view"));
        when(roleMapper.selectByCodes(List.of("EMPLOYEE"))).thenReturn(List.of(role("EMPLOYEE", "普通员工")));
        when(jwtTokenProvider.createToken(eq(1L), eq("alice"), eq(false), eq(false),
                anyList(), eq(4L))).thenReturn("new-token");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("old");
        request.setNewPassword("newpass");
        AuthUser authUser = new AuthUser(1L, "alice", false, true,
                Set.of("EMPLOYEE"), Set.of());

        LoginResponse response = authService.changePassword(authUser, request);

        assertThat(response.getToken()).isEqualTo("new-token");
        assertThat(response.getMustChangePassword()).isFalse();
        verify(userAuthStateService).bumpAndEvict(1L);
        verify(sysUserMapper).updateById(any(SysUser.class));
    }
}
