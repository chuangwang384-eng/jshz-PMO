package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.UserRequest;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户管理服务（正式版 V1.001，管理员名单管理）。
 * 新建 / 修改 / 停用 / 重置密码（重置为 111111）。
 */
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<SysUserMapper, SysUser> {

    public static final String DEFAULT_PASSWORD = "111111";

    /** 姓名格式：2-20 个汉字，可含间隔号 · */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,20}$");

    private final PasswordEncoder passwordEncoder;

    /**
     * 用户列表（不含密码）。
     */
    public List<SysUser> listUsers(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword);
        }
        wrapper.orderByAsc(SysUser::getId);
        return this.list(wrapper);
    }

    /**
     * 新建用户：账号 = 姓名，密码默认 111111，首次登录强制改密。
     */
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
        return user;
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
     * 重置密码为 111111，并标记强制改密。
     */
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        this.updateById(user);
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
}
