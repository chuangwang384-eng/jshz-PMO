package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.UserRequest;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.entity.WecomDepartment;
import com.jshz.expert.entity.WecomUserDepartment;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import com.jshz.expert.mapper.WecomDepartmentMapper;
import com.jshz.expert.mapper.WecomUserDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户管理服务（正式版 V1.001，管理员名单管理）。
 * 新建 / 修改 / 停用 / 重置密码（重置为 111111）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<SysUserMapper, SysUser> {

    public static final String DEFAULT_PASSWORD = "111111";

    /** 姓名格式：2-20 个汉字，可含间隔号 · */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,20}$");

    private final PasswordEncoder passwordEncoder;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final WecomUserDepartmentMapper wecomUserDepartmentMapper;
    private final WecomDepartmentMapper wecomDepartmentMapper;

    /**
     * 用户列表（不含密码）。
     */
    public List<SysUser> listUsers(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword);
        }
        wrapper.orderByAsc(SysUser::getId);
        List<SysUser> users = this.list(wrapper);
        fillRoles(users);
        fillDepartments(users);
        return users;
    }

    /**
     * 新建用户：账号 = 姓名，密码默认 111111，首次登录强制改密。
     * 创建成功后在同一事务内绑定默认角色 EMPLOYEE（未分配角色用户默认普通员工）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(UserRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new BusinessException("账号不能为空");
        }
        String username = request.getUsername().trim();
        // 普通员工账号必须是姓名格式（管理员账号不受此限制）
        if (!Boolean.TRUE.equals(request.getIsAdmin()) && !NAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException("账号格式错误：请输入 2-20 个汉字的姓名");
        }
        Long exists = this.baseMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException("账号已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setIsAdmin(Boolean.TRUE.equals(request.getIsAdmin()));
        user.setMustChangePassword(true);
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        this.save(user);
        // 默认角色：未分配角色用户一律绑定普通员工(EMPLOYEE)，与创建同一事务
        bindDefaultEmployeeRole(user.getId());
        return user;
    }

    /**
     * 批量填充用户主部门名称（企微通讯录同步后，非表字段 departmentName）。
     * 仅取 is_main=1 的主部门；无绑定部门或企微未同步时不填充。
     */
    private void fillDepartments(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        List<WecomUserDepartment> bindings = wecomUserDepartmentMapper.selectList(
                new LambdaQueryWrapper<WecomUserDepartment>()
                        .in(WecomUserDepartment::getUserId, userIds)
                        .eq(WecomUserDepartment::getIsMain, 1));
        if (bindings.isEmpty()) {
            return;
        }
        Set<Long> deptIds = bindings.stream().map(WecomUserDepartment::getDeptId).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = wecomDepartmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(WecomDepartment::getDeptId, WecomDepartment::getName, (a, b) -> a));
        Map<Long, String> byUser = bindings.stream()
                .collect(Collectors.toMap(WecomUserDepartment::getUserId,
                        w -> deptNameMap.getOrDefault(w.getDeptId(), null), (a, b) -> a));
        for (SysUser u : users) {
            u.setDepartmentName(byUser.get(u.getId()));
        }
    }

    /**
     * 绑定默认角色 EMPLOYEE（幂等）：
     * - 按 role.code='EMPLOYEE' 查库获取 roleId，禁止硬编码 roleId；
     * - EMPLOYEE 角色不存在时记录告警日志并跳过，不阻断主流程；
     * - 已存在 (user_id, role_id) 绑定则跳过，避免重复与唯一键冲突。
     * 供创建用户 / 自动注册复用；事务由调用方或本方法保证。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindDefaultEmployeeRole(Long userId) {
        Role employeeRole = roleMapper.selectByCode("EMPLOYEE");
        if (employeeRole == null) {
            log.warn("绑定默认角色失败：角色 EMPLOYEE 不存在（builtin 角色缺失），用户 {} 将保持无角色状态", userId);
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, employeeRole.getId()));
        if (count != null && count > 0) {
            return; // 已绑定，幂等跳过
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(employeeRole.getId());
        userRoleMapper.insert(ur);
    }

    /**
     * 修改用户：账号 / 管理员标记 / 状态。
     */
    public SysUser updateUser(Long id, UserRequest request) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (request == null) {
            return user;
        }
        if (StringUtils.hasText(request.getUsername())) {
            String username = request.getUsername().trim();
            Long exists = this.baseMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .ne(SysUser::getId, id));
            if (exists != null && exists > 0) {
                throw new BusinessException("账号已存在");
            }
            user.setUsername(username);
        }
        if (request.getIsAdmin() != null) {
            user.setIsAdmin(request.getIsAdmin());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        this.updateById(user);
        return user;
    }

    /**
     * 重置密码为 111111，并标记强制改密。重置后通知用户。
     */
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        this.updateById(user);
        notificationService.notify(user.getId(), NotificationService.TYPE_PASSWORD_RESET,
                "密码已重置",
                "您的登录密码已被管理员重置为初始密码 " + DEFAULT_PASSWORD + "，请登录后立即修改",
                "/profile/change-password");
    }

    /**
     * 删除用户（管理员专用）。
     */
    public void deleteUser(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("用户不存在");
        }
        this.removeById(id);
    }

    /**
     * 为用户分配角色（覆盖式，RBAC 阶段1）。
     * 先删除该用户全部 user_role 再插入，随后清权限缓存即时生效。
     */
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Set<Long> distinct = new LinkedHashSet<>(roleIds == null ? List.of() : roleIds);
        if (!distinct.isEmpty()) {
            List<Role> roles = roleMapper.selectBatchIds(distinct);
            if (roles.size() != distinct.size()) {
                throw new BusinessException("存在无效的角色");
            }
            boolean hasDisabled = roles.stream().anyMatch(r -> r.getStatus() == null || r.getStatus() != 1);
            if (hasDisabled) {
                throw new BusinessException("不能分配已停用的角色");
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : distinct) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        permissionService.clearCaches();
        notificationService.notify(userId, NotificationService.TYPE_ROLE_CHANGED,
                "角色已调整",
                "您的系统角色已被管理员调整，请重新登录以刷新权限",
                "/profile");
    }

    /**
     * 批量填充用户角色名称与角色 ID（RBAC 阶段1，供前端列表展示与分配回显）。
     */
    private void fillRoles(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        List<UserRole> bindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
        if (bindings.isEmpty()) {
            return;
        }
        Map<Long, Role> roleMap = roleMapper.selectBatchIds(
                        bindings.stream().map(UserRole::getRoleId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        Map<Long, List<UserRole>> byUser = bindings.stream()
                .collect(Collectors.groupingBy(UserRole::getUserId));
        for (SysUser u : users) {
            List<UserRole> urs = byUser.getOrDefault(u.getId(), List.of());
            u.setRoleIds(urs.stream().map(UserRole::getRoleId).collect(Collectors.toList()));
            u.setRoles(urs.stream()
                    .map(ur -> roleMap.get(ur.getRoleId()))
                    .filter(Objects::nonNull)
                    .map(Role::getName)
                    .collect(Collectors.toList()));
        }
    }
}
