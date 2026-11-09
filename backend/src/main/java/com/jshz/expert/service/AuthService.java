package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.config.WecomProperties;
import com.jshz.expert.dto.ChangePasswordRequest;
import com.jshz.expert.dto.LoginRequest;
import com.jshz.expert.dto.LoginResponse;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 认证服务（正式版 V1.001；2026-10-27 企微账号体系改造）。
 * 登录标识三模式：NAME（姓名+自动注册，默认/回滚档）/ COMPAT（姓名未绑定或手机号，过渡）/
 * MOBILE（仅手机号，关闭自动注册，管理员豁免）。停用检查、BCrypt、JWT、cookie、强制改密流程不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PermissionService permissionService;
    private final UserAuthStateService userAuthStateService;
    private final UserService userService;
    private final WecomProperties wecomProperties;

    /** 手机号格式：1 开头、第二位 3-9、共 11 位数字 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 登录：正常登录为只读流程；自动注册时注册 + 默认角色绑定须在同一事务内，
     * 故在入口开启事务（自调用不经过代理，@Transactional 需标注在本方法）。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        String identifier = request.getUsername().trim();
        String mode = normalizeLoginMode();
        SysUser user = resolveUserByIdentifier(identifier, mode);
        if (user == null) {
            if ("MOBILE".equals(mode)) {
                // 终态：账号只来自通讯录同步或管理员创建，关闭自动注册
                throw new BusinessException(401, "账号不存在或手机号错误（账号仅来自企微通讯录同步/管理员创建）");
            }
            // NAME / COMPAT：查不到用户 → 姓名格式 + 初始密码 111111 → 自动注册并登录
            return autoRegisterAndLogin(identifier, request.getPassword(), mode);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return buildLoginResponse(user);
    }

    /** 归一化登录模式（WecomProperties 启动时已校验合法性） */
    private String normalizeLoginMode() {
        String mode = wecomProperties.getLoginMode();
        return mode == null ? "NAME" : mode.trim().toUpperCase();
    }

    /**
     * 按登录标识解析用户：
     * - 输入为手机号格式：一律按 mobile 精确查（NAME 模式本就不支持手机号，按 mobile 查自然无结果）；
     * - NAME：按 username 精确查（完全等同现状）；
     * - COMPAT：姓名通道仅对未绑定用户（wecom_userid IS NULL）开放；
     * - MOBILE：仅手机号通道；管理员（is_admin=1）豁免，可用姓名登录。
     */
    private SysUser resolveUserByIdentifier(String identifier, String mode) {
        if (MOBILE_PATTERN.matcher(identifier).matches()) {
            return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getMobile, identifier)
                    .last("LIMIT 1"));
        }
        if ("MOBILE".equals(mode)) {
            // 管理员豁免：MOBILE 模式仍允许 is_admin=1 用姓名登录
            return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, identifier)
                    .eq(SysUser::getIsAdmin, true)
                    .last("LIMIT 1"));
        }
        if ("COMPAT".equals(mode)) {
            return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, identifier)
                    .and(w -> w.isNull(SysUser::getWecomUserId).or().eq(SysUser::getWecomUserId, ""))
                    .last("LIMIT 1"));
        }
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, identifier)
                .last("LIMIT 1"));
    }

    /** 姓名格式：2-20 个汉字，可含间隔号 ·（如“布格拉·米吉提”） */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,20}$");

    /**
     * 自动注册并登录：仅当账号符合姓名格式且密码为初始密码 111111 时允许。
     * 注册数据：is_admin=0、must_change_password=1、status=1、密码 BCrypt 加密。
     * 并发重复注册由 uk_username 唯一索引兜底，冲突时回查后走正常登录流程。
     *
     * <p>企微账号体系改造后：</p>
     * <ul>
     *   <li>uk_username 已删除（迁移 20261026），重名数据允许进入；应用层约束未绑定通道内姓名唯一；</li>
     *   <li>NAME 模式：行为完全等同现状；</li>
     *   <li>COMPAT 模式：仅当未绑定通道内该姓名唯一才允许自动注册；若已有同名已绑定用户则拒绝（歧义，需人工指认或手机号登录）；</li>
     *   <li>MOBILE 模式：关闭自动注册（本方法不会被调用，防御性兜底）。</li>
     * </ul>
     */
    private LoginResponse autoRegisterAndLogin(String username, String rawPassword, String mode) {
        if (!NAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(400, "账号格式错误：请输入 2-20 个汉字的姓名");
        }
        if ("COMPAT".equals(mode)) {
            // 未绑定通道内姓名唯一性检查：若存在同名已绑定用户，拒绝自动注册
            Long boundSameName = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .and(w -> w.isNotNull(SysUser::getWecomUserId).ne(SysUser::getWecomUserId, "")));
            if (boundSameName != null && boundSameName > 0) {
                throw new BusinessException(400, "系统已有同名企微绑定账号，请使用手机号登录或联系管理员绑定");
            }
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
        user.setTokenVersion(0);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发重复注册：唯一索引冲突，回查已注册用户后走正常登录流程
            user = resolveUserByIdentifier(username, mode);
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
        // 自动注册成功后绑定默认角色 EMPLOYEE（幂等；与注册同一事务，失败整体回滚）
        userService.bindDefaultEmployeeRole(user.getId());
        return buildLoginResponse(user);
    }

    /** 构建登录响应（JWT + 角色/权限） */
    private LoginResponse buildLoginResponse(SysUser user) {
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        Long userId = user.getId();
        List<String> roles = new ArrayList<>(permissionService.getUserRoles(userId));
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion().longValue();
        String token = jwtTokenProvider.createToken(userId, user.getUsername(),
                Boolean.TRUE.equals(user.getIsAdmin()), mustChange, roles, tokenVersion);
        return new LoginResponse(token, user.getUsername(), user.getIsAdmin(), mustChange, roles,
                new ArrayList<>(permissionService.getUserPermissions(userId)),
                user.getId(), user.getCreateTime(), toRoleNames(roles));
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
                new ArrayList<>(permissionService.getUserPermissions(userId)),
                user.getId(), user.getCreateTime(), toRoleNames(roles));
    }

    /**
     * 修改密码：校验旧密码，更新新密码并清除强制改密标记。
     *
     * <p>P1-2：修改成功后自增 token_version，使其他会话的旧 Token 立即失效；
     * 返回携带新版本号的新登录响应，由控制器写回 Cookie，当前会话无需重新登录。</p>
     *
     * <p>密码更新与版本自增放在同一事务，避免只改密码未吊销旧 Token。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse changePassword(AuthUser authUser, ChangePasswordRequest request) {
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
        userAuthStateService.bumpAndEvict(user.getId());
        SysUser fresh = sysUserMapper.selectById(user.getId());
        return buildLoginResponse(fresh == null ? user : fresh);
    }

    /**
     * 角色编码列表 → 友好名称列表。
     *
     * <p>名称唯一权威来源为数据库 role 表的 name 字段（不再使用静态映射，
     * 避免漏掉库内新增角色导致个人中心中英混合显示）。单次批量查询，按用户原有
     * 角色编码顺序组装，不依赖数据库返回顺序；空角色集合不做任何查询。</p>
     *
     * <p>异常与兜底策略：</p>
     * <ul>
     *   <li>情况A：查询成功但某编码在 role 表无记录 → 回退为该编码本身，记录 warn 日志；</li>
     *   <li>情况B：记录存在但 name 为空 → 回退为该编码本身（不返回空字符串作为正常展示名称），记录 warn 日志；</li>
     *   <li>情况C：数据库查询失败 → 不当普通缺失处理、不静默伪装，转为业务异常上报（日志与响应均不泄露 SQL/连接细节）。</li>
     * </ul>
     */
    List<String> toRoleNames(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Role> roleByCode;
        try {
            roleByCode = roleMapper.selectByCodes(roles).stream()
                    .collect(Collectors.toMap(Role::getCode, r -> r, (a, b) -> a, HashMap::new));
        } catch (DataAccessException e) {
            // 情况C：数据库查询失败，不得当普通缺失处理、不得捕获后静默返回原编码伪装成正常结果
            log.error("角色名称动态查询失败（role 表），涉及角色编码数={}，异常类型={}",
                    roles.size(), e.getClass().getSimpleName());
            throw new BusinessException(500, "获取角色名称失败，请稍后重试");
        }
        List<String> names = new ArrayList<>(roles.size());
        for (String code : roles) {
            if (code == null || code.isEmpty()) {
                names.add(code);
                continue;
            }
            Role role = roleByCode.get(code);
            if (role == null) {
                // 情况A：查询成功但该编码无记录，回退为编码本身
                log.warn("角色名称缺失：role 表无 code={} 的记录，回退为编码本身", code);
                names.add(code);
            } else if (!StringUtils.hasText(role.getName())) {
                // 情况B：记录存在但 name 为空，回退为编码本身，不伪造中文名称
                log.warn("角色名称缺失：code={} 的记录 name 为空，回退为编码本身", code);
                names.add(code);
            } else {
                names.add(role.getName());
            }
        }
        return names;
    }
}
