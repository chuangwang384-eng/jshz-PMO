package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.ChangePasswordRequest;
import com.jshz.expert.dto.LoginRequest;
import com.jshz.expert.dto.LoginResponse;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 认证服务（正式版 V1.001）。
 * 账号 = 员工姓名登录、BCrypt 校验、JWT 签发、强制改密。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PermissionService permissionService;

    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername().trim()));
        if (user == null) {
            // 查不到用户：姓名格式 + 初始密码 111111 → 自动注册并登录（首次使用免注册）
            return autoRegisterAndLogin(request.getUsername().trim(), request.getPassword());
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        Long userId = user.getId();
        List<String> roles = new ArrayList<>(permissionService.getUserRoles(userId));
        String token = jwtTokenProvider.createToken(userId, user.getUsername(),
                Boolean.TRUE.equals(user.getIsAdmin()), mustChange, roles);
        return new LoginResponse(token, user.getUsername(), user.getIsAdmin(), mustChange, roles,
                new ArrayList<>(permissionService.getUserPermissions(userId)));
    }

    /** 姓名格式：2-20 个汉字，可含间隔号 ·（如“布格拉·米吉提”） */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,20}$");

    /**
     * 自动注册并登录：仅当账号符合姓名格式且密码为初始密码 111111 时允许。
     * 注册数据：is_admin=0、must_change_password=1、status=1、密码 BCrypt 加密。
     * 并发重复注册由 uk_username 唯一索引兜底，冲突时回查后走正常登录流程。
     */
    private LoginResponse autoRegisterAndLogin(String username, String rawPassword) {
        if (!NAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(400, "账号格式错误：请输入 2-20 个汉字的姓名");
        }
        if (!passwordEncoder.matches(rawPassword, passwordEncoder.encode(UserService.DEFAULT_PASSWORD))) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UserService.DEFAULT_PASSWORD));
        user.setIsAdmin(false);
        user.setMustChangePassword(true);
        user.setStatus(1);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发重复注册：唯一索引冲突，回查已注册用户后走正常登录流程
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (user == null) {
                throw e;
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(403, "账号已停用，请联系管理员");
            }
            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                throw new BusinessException(401, "用户名或密码错误");
            }
        }
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        Long userId = user.getId();
        List<String> roles = new ArrayList<>(permissionService.getUserRoles(userId));
        String token = jwtTokenProvider.createToken(userId, user.getUsername(),
                Boolean.TRUE.equals(user.getIsAdmin()), mustChange, roles);
        return new LoginResponse(token, user.getUsername(), user.getIsAdmin(), mustChange, roles,
                new ArrayList<>(permissionService.getUserPermissions(userId)));
    }

    /**
     * 当前登录用户信息（从数据库实时读取，保证强制改密标记最新）。
     */
    public LoginResponse me(AuthUser authUser) {
        SysUser user = sysUserMapper.selectById(authUser.getUserId());
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        Long userId = user.getId();
        List<String> roles = new ArrayList<>(permissionService.getUserRoles(userId));
        return new LoginResponse(null, user.getUsername(), user.getIsAdmin(),
                Boolean.TRUE.equals(user.getMustChangePassword()), roles,
                new ArrayList<>(permissionService.getUserPermissions(userId)));
    }

    /**
     * 修改密码：校验旧密码，更新新密码并清除强制改密标记。
     */
    public void changePassword(AuthUser authUser, ChangePasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.getOldPassword()) || !StringUtils.hasText(request.getNewPassword())) {
            throw new BusinessException("旧密码和新密码不能为空");
        }
        if (request.getNewPassword().length() < 6) {
            throw new BusinessException("新密码长度不能少于 6 位");
        }
        SysUser user = sysUserMapper.selectById(authUser.getUserId());
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        sysUserMapper.updateById(user);
    }
}
