package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.client.WecomApiClient;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.config.WecomProperties;
import com.jshz.expert.dto.DepartmentNodeVo;
import com.jshz.expert.dto.UnboundDataVo;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.WecomDepartment;
import com.jshz.expert.entity.WecomSyncLog;
import com.jshz.expert.entity.WecomUserDepartment;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.WecomDepartmentMapper;
import com.jshz.expert.mapper.WecomSyncLogMapper;
import com.jshz.expert.mapper.WecomUserDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 企微通讯录同步核心服务（2026-10-27 评审通过）。
 *
 * <p>同步策略（见设计文档 §6.3）：</p>
 * <ol>
 *   <li>并发护栏：存在 RUNNING 同步记录 → 抛 409"同步进行中"；</li>
 *   <li>拉部门树 → upsert wecom_department（企微已删 → status=0）；</li>
 *   <li>拉成员 ID 全量 → 逐人详情，按 wecom_userid diff：
 *       a. 命中已绑定 → 更新姓名/手机号/部门归属；
 *       b. 未命中且企微姓名与本系统未绑定用户唯一匹配 → 自动绑定；
 *       c. 未命中且无唯一匹配 → 新建（111111 + 强制改密 + 默认 EMPLOYEE）；
 *       d. status ∈ {2,5} → 停用；</li>
 *   <li>本轮全量未出现的已绑定用户 → 停用（离职）；</li>
 *   <li>upsert wecom_user_department（主部门标记）；</li>
 *   <li>写 wecom_sync_log（DryRun 只写将变更清单，不落业务数据）。</li>
 * </ol>
 *
 * <p>手机号一律脱敏日志（138****1234）；secret/token 不出现在日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactSyncService {

    /** 企微成员状态：1 已激活 / 2 已禁用 / 4 未激活 / 5 退出企业 */
    private static final int WECOM_STATUS_DISABLED = 2;
    private static final int WECOM_STATUS_LEFT = 5;

    private final WecomApiClient wecomApiClient;
    private final WecomProperties wecomProperties;
    private final WecomDepartmentMapper wecomDepartmentMapper;
    private final WecomUserDepartmentMapper wecomUserDepartmentMapper;
    private final WecomSyncLogMapper wecomSyncLogMapper;
    private final SysUserMapper sysUserMapper;
    private final UserAuthStateService userAuthStateService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final WecomSyncTrigger wecomSyncTrigger;

    /** 定时同步开关（读取 expert.wecom.sync-cron-enabled） */
    public boolean isSyncCronEnabled() {
        return wecomProperties.isSyncCronEnabled();
    }

    /**
     * 启动同步（手动/定时共用入口）。
     * 并发护栏 → 建 RUNNING 日志 → 异步执行，立即返回 logId。
     *
     * @throws BusinessException 已有同步进行中（409 语义）
     */
    public Long start(String triggerType, String operator, boolean dryRun) {
        Long running = wecomSyncLogMapper.selectCount(new LambdaQueryWrapper<WecomSyncLog>()
                .eq(WecomSyncLog::getStatus, "RUNNING"));
        if (running != null && running > 0) {
            throw new BusinessException(409, "同步进行中，请稍后再试");
        }
        WecomSyncLog logEntity = new WecomSyncLog();
        logEntity.setTriggerType(dryRun ? "DRYRUN" : triggerType);
        logEntity.setOperator(operator);
        logEntity.setStatus("RUNNING");
        logEntity.setStartTime(LocalDateTime.now());
        wecomSyncLogMapper.insert(logEntity);
        Long logId = logEntity.getId();
        wecomSyncTrigger.runAsync(logId, dryRun);
        return logId;
    }

    /** DryRun 变更清单单项 */
    public static class ChangeItem {
        public String action;      // CREATE/BIND/UPDATE/DISABLE/UNBOUND/IGNORED
        public String wecomUserId;
        public String name;
        public String mobileMasked;
        public String reason;
    }

    /** 异步执行同步主体（由 WecomSyncTrigger 代理调用，保证 @Async 生效） */
    public void execute(Long logId, boolean dryRun) {
        WecomSyncLog logEntity = wecomSyncLogMapper.selectById(logId);
        if (logEntity == null) {
            log.warn("企微同步日志 {} 不存在，终止执行", logId);
            return;
        }
        List<ChangeItem> changes = new ArrayList<>();
        int created = 0, updated = 0, disabled = 0, bound = 0, fetched = 0;
        try {
            // 1. 拉部门
            List<WecomApiClient.DeptInfo> depts = wecomApiClient.listDepartments();
            // 2. 拉成员 ID 全量
            List<WecomApiClient.MemberRef> refs = wecomApiClient.listUserIds();
            fetched = refs.size();
            Set<String> allUserIds = new LinkedHashSet<>();
            for (WecomApiClient.MemberRef ref : refs) {
                allUserIds.add(ref.userId);
            }

            // 3. 逐人详情 + diff
            Map<String, List<Long>> userDeptMap = new HashMap<>();
            for (WecomApiClient.MemberRef ref : refs) {
                WecomApiClient.MemberDetail detail = wecomApiClient.getUserDetail(ref.userId);
                userDeptMap.put(detail.userId, detail.departments);
                boolean left = detail.status == WECOM_STATUS_DISABLED || detail.status == WECOM_STATUS_LEFT;
                SysUser existing = findBoundByWecomUserId(detail.userId);
                if (existing != null) {
                    if (left) {
                        if (existing.getStatus() != null && existing.getStatus() == 1) {
                            existing.setStatus(0);
                            sysUserMapper.updateById(existing);
                            // P1-2：企微离职/禁用 → 已登录会话立即失效
                            userAuthStateService.bumpAndEvict(existing.getId());
                            disabled++;
                            changes.add(item("DISABLE", detail, "企微成员已离职/禁用，停用账号"));
                        } else {
                            changes.add(item("IGNORED", detail, "企微成员已离职/禁用，账号已停用"));
                        }
                    } else {
                        boolean changed = applyUserUpdate(existing, detail, changes);
                        if (changed) {
                            updated++;
                        }
                    }
                } else {
                    List<SysUser> candidates = findUnboundByUsername(detail.name);
                    if (left) {
                        changes.add(item("IGNORED", detail, "企微成员已离职/禁用，跳过绑定/新建"));
                    } else if (candidates.size() == 1) {
                        bindExisting(candidates.get(0), detail, userDeptMap);
                        bound++;
                        changes.add(item("BIND", detail, "按姓名唯一匹配自动绑定存量账号"));
                    } else if (candidates.size() > 1) {
                        changes.add(item("UNBOUND", detail, "系统侧存在多个同名未绑定账号，需人工指认"));
                    } else {
                        if (dryRun) {
                            changes.add(item("CREATE", detail, "无匹配账号，将新建（初始密码111111+强制改密）"));
                        } else {
                            SysUser createdUser = createFromWecom(detail);
                            created++;
                            changes.add(item("CREATE", detail, "无匹配账号，新建账号"));
                            log.info("企微同步新建账号：userid={} username={} mobile={}",
                                    detail.userId, createdUser.getUsername(),
                                    WecomApiClient.maskMobile(createdUser.getMobile()));
                        }
                    }
                }
            }

            // 4. 本轮全量未出现的已绑定用户 → 停用（离职）
            List<SysUser> boundUsers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .isNotNull(SysUser::getWecomUserId)
                    .ne(SysUser::getWecomUserId, ""));
            for (SysUser u : boundUsers) {
                if (!allUserIds.contains(u.getWecomUserId()) && u.getStatus() != null && u.getStatus() == 1) {
                    u.setStatus(0);
                    sysUserMapper.updateById(u);
                    // P1-2：本轮未出现视为离职 → 已登录会话立即失效
                    userAuthStateService.bumpAndEvict(u.getId());
                    disabled++;
                    changes.add(item("DISABLE", u.getWecomUserId(), u.getUsername(),
                            WecomApiClient.maskMobile(u.getMobile()), "本轮企微全量未出现，视为离职停用"));
                }
            }

            // 5. 部门 upsert + 成员归属（DryRun 不写）
            if (!dryRun) {
                upsertDepartments(depts);
                upsertUserDepartments(userDeptMap);
                // 企微已删除部门置 0
                markDeletedDepartments(depts);
            }

            // 6. 写日志
            int unboundLeft = countUnbound();
            logEntity.setStatus("SUCCESS");
            logEntity.setFetchedUsers(fetched);
            logEntity.setCreatedUsers(created);
            logEntity.setUpdatedUsers(updated);
            logEntity.setDisabledUsers(disabled);
            logEntity.setBoundUsers(bound);
            logEntity.setUnboundLeft(unboundLeft);
            logEntity.setEndTime(LocalDateTime.now());
            if (dryRun) {
                logEntity.setDetail(toJson(changes));
            } else {
                logEntity.setDetail(null);
            }
            wecomSyncLogMapper.updateById(logEntity);
            log.info("企微同步完成 logId={} dryRun={} fetched={} created={} updated={} bound={} disabled={} unboundLeft={}",
                    logId, dryRun, fetched, created, updated, bound, disabled, unboundLeft);
        } catch (Exception e) {
            logEntity.setStatus("FAILED");
            logEntity.setErrorMsg(StringUtils.hasText(e.getMessage()) ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : e.getClass().getSimpleName());
            logEntity.setEndTime(LocalDateTime.now());
            wecomSyncLogMapper.updateById(logEntity);
            log.error("企微同步失败 logId={} dryRun={}", logId, dryRun, e);
        }
    }

    // ---------------- diff 辅助 ----------------

    private SysUser findBoundByWecomUserId(String wecomUserId) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getWecomUserId, wecomUserId)
                .last("LIMIT 1"));
    }

    private List<SysUser> findUnboundByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .and(w -> w.isNull(SysUser::getWecomUserId).or().eq(SysUser::getWecomUserId, "")));
    }

    private int countUnbound() {
        Long n = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .and(w -> w.isNull(SysUser::getWecomUserId).or().eq(SysUser::getWecomUserId, "")));
        return n == null ? 0 : n.intValue();
    }

    /** 更新已绑定用户姓名/手机号/状态；返回是否发生变更 */
    private boolean applyUserUpdate(SysUser user, WecomApiClient.MemberDetail detail, List<ChangeItem> changes) {
        boolean changed = false;
        boolean statusReenabled = false;
        StringBuilder reason = new StringBuilder("更新：");
        if (StringUtils.hasText(detail.name) && !detail.name.equals(user.getUsername())) {
            // 姓名冲突（重名用户已存在）时保留现有姓名，避免破坏
            Long conflict = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, detail.name)
                    .ne(SysUser::getId, user.getId()));
            if (conflict == null || conflict == 0) {
                user.setUsername(detail.name);
                reason.append("姓名→").append(detail.name).append("; ");
                changed = true;
            } else {
                reason.append("姓名冲突保留现有; ");
            }
        }
        if (StringUtils.hasText(detail.mobile) && !detail.mobile.equals(user.getMobile())) {
            Long conflict = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getMobile, detail.mobile)
                    .ne(SysUser::getId, user.getId()));
            if (conflict == null || conflict == 0) {
                user.setMobile(detail.mobile);
                reason.append("手机号→").append(WecomApiClient.maskMobile(detail.mobile)).append("; ");
                changed = true;
            } else {
                reason.append("手机号被占用保留现有; ");
            }
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            user.setStatus(1);
            reason.append("恢复启用; ");
            changed = true;
            statusReenabled = true;
        }
        if (changed) {
            sysUserMapper.updateById(user);
            if (statusReenabled) {
                // P1-2：重新启用时自增版本，避免停用期间签发的旧 Token 复活
                userAuthStateService.bumpAndEvict(user.getId());
            }
            changes.add(item("UPDATE", detail, reason.toString()));
        }
        return changed;
    }

    /** 自动绑定存量账号（双方唯一匹配） */
    private void bindExisting(SysUser user, WecomApiClient.MemberDetail detail, Map<String, List<Long>> userDeptMap) {
        boolean reenabled = false;
        user.setWecomUserId(detail.userId);
        if (StringUtils.hasText(detail.mobile)) {
            Long conflict = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getMobile, detail.mobile)
                    .ne(SysUser::getId, user.getId()));
            if (conflict == null || conflict == 0) {
                user.setMobile(detail.mobile);
            }
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            user.setStatus(1);
            reenabled = true;
        }
        sysUserMapper.updateById(user);
        if (reenabled) {
            // P1-2：重新启用时自增版本，旧 Token 失效
            userAuthStateService.bumpAndEvict(user.getId());
        }
    }

    /** 新建企微成员账号（实跑）：username=姓名（允许重名），默认密码+强制改密+EMPLOYEE */
    @Transactional(rollbackFor = Exception.class)
    public SysUser createFromWecom(WecomApiClient.MemberDetail detail) {
        SysUser user = new SysUser();
        user.setUsername(detail.name);
        user.setPassword(passwordEncoder.encode(UserService.DEFAULT_PASSWORD));
        user.setWecomUserId(detail.userId);
        if (StringUtils.hasText(detail.mobile)) {
            Long conflict = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getMobile, detail.mobile));
            if (conflict == null || conflict == 0) {
                user.setMobile(detail.mobile);
            }
        }
        user.setIsAdmin(false);
        user.setMustChangePassword(true);
        user.setStatus(1);
        user.setTokenVersion(0);
        sysUserMapper.insert(user);
        userService.bindDefaultEmployeeRole(user.getId());
        return user;
    }

    /** upsert 部门快照（实跑） */
    private void upsertDepartments(List<WecomApiClient.DeptInfo> depts) {
        for (WecomApiClient.DeptInfo d : depts) {
            WecomDepartment existing = wecomDepartmentMapper.selectOne(
                    new LambdaQueryWrapper<WecomDepartment>().eq(WecomDepartment::getDeptId, d.deptId));
            if (existing == null) {
                WecomDepartment entity = new WecomDepartment();
                entity.setDeptId(d.deptId);
                entity.setName(d.name);
                entity.setParentDeptId(d.parentId != null && d.parentId == 0L ? null : d.parentId);
                entity.setSortOrder(d.order);
                entity.setStatus(1);
                wecomDepartmentMapper.insert(entity);
            } else {
                existing.setName(d.name);
                existing.setParentDeptId(d.parentId != null && d.parentId == 0L ? null : d.parentId);
                existing.setSortOrder(d.order);
                existing.setStatus(1);
                wecomDepartmentMapper.updateById(existing);
            }
        }
    }

    /** upsert 成员-部门归属（实跑）：按 sys_user 主部门/非主部门重建 */
    private void upsertUserDepartments(Map<String, List<Long>> userDeptMap) {
        List<SysUser> bound = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .isNotNull(SysUser::getWecomUserId)
                .ne(SysUser::getWecomUserId, ""));
        for (SysUser u : bound) {
            List<Long> deptIds = userDeptMap.get(u.getWecomUserId());
            wecomUserDepartmentMapper.delete(new LambdaQueryWrapper<WecomUserDepartment>()
                    .eq(WecomUserDepartment::getUserId, u.getId()));
            if (deptIds == null || deptIds.isEmpty()) {
                continue;
            }
            for (int i = 0; i < deptIds.size(); i++) {
                WecomUserDepartment wud = new WecomUserDepartment();
                wud.setUserId(u.getId());
                wud.setDeptId(deptIds.get(i));
                wud.setIsMain(i == 0 ? 1 : 0);
                wecomUserDepartmentMapper.insert(wud);
            }
        }
    }

    /** 企微已删除部门置 0（保留快照） */
    private void markDeletedDepartments(List<WecomApiClient.DeptInfo> depts) {
        Set<Long> alive = new LinkedHashSet<>();
        for (WecomApiClient.DeptInfo d : depts) {
            alive.add(d.deptId);
        }
        List<WecomDepartment> all = wecomDepartmentMapper.selectList(null);
        for (WecomDepartment d : all) {
            if (!alive.contains(d.getDeptId()) && d.getStatus() != null && d.getStatus() == 1) {
                d.setStatus(0);
                wecomDepartmentMapper.updateById(d);
            }
        }
    }

    // ---------------- 日志 / 变更项 ----------------

    private ChangeItem item(String action, WecomApiClient.MemberDetail d, String reason) {
        return item(action, d.userId, d.name, WecomApiClient.maskMobile(d.mobile), reason);
    }

    private ChangeItem item(String action, String userId, String name, String mobileMasked, String reason) {
        ChangeItem c = new ChangeItem();
        c.action = action;
        c.wecomUserId = userId;
        c.name = name;
        c.mobileMasked = mobileMasked;
        c.reason = reason;
        return c;
    }

    private String toJson(List<ChangeItem> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 最近一次同步的 UNBOUND/IGNORED 项（人工指认数据源） */
    public List<Map<String, Object>> latestUnboundFromLog() {
        WecomSyncLog latest = wecomSyncLogMapper.selectOne(
                new LambdaQueryWrapper<WecomSyncLog>()
                        .orderByDesc(WecomSyncLog::getId)
                        .last("LIMIT 1"));
        if (latest == null || !StringUtils.hasText(latest.getDetail())) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(latest.getDetail(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> m : list) {
                String action = String.valueOf(m.getOrDefault("action", ""));
                if ("UNBOUND".equals(action) || "IGNORED".equals(action)) {
                    result.add(m);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析同步日志 detail 失败", e);
            return List.of();
        }
    }

    /**
     * 管理员人工指认绑定（重名/改名兜底）。
     * 企微凭证可用时校验成员状态与手机号；不可用时仅绑定 userid，手机号留待下次同步。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUser bindWecom(Long userId, String wecomUserId) {
        if (!StringUtils.hasText(wecomUserId)) {
            throw new BusinessException("企微 userid 不能为空");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Long occupied = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getWecomUserId, wecomUserId)
                .ne(SysUser::getId, userId));
        if (occupied != null && occupied > 0) {
            throw new BusinessException(409, "该企微成员已绑定其他账号");
        }
        if (StringUtils.hasText(user.getWecomUserId())) {
            throw new BusinessException("该账号已绑定企微成员，请先解绑");
        }
        String mobile = null;
        String name = null;
        if (wecomProperties.hasCredential()) {
            WecomApiClient.MemberDetail detail = wecomApiClient.getUserDetail(wecomUserId);
            if (detail.status == WECOM_STATUS_DISABLED || detail.status == WECOM_STATUS_LEFT) {
                throw new BusinessException("企微成员已离职/禁用，不允许绑定");
            }
            mobile = detail.mobile;
            name = detail.name;
        }
        user.setWecomUserId(wecomUserId);
        if (StringUtils.hasText(mobile)) {
            Long conflict = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getMobile, mobile)
                    .ne(SysUser::getId, userId));
            if (conflict == null || conflict == 0) {
                user.setMobile(mobile);
            }
        }
        if (StringUtils.hasText(name) && !StringUtils.hasText(user.getMobile())) {
            // 保持 username 不变（人工指认场景多为重名/改名）；不覆盖
        }
        sysUserMapper.updateById(user);
        return user;
    }

    // ---------------- 查询接口辅助（Controller 复用） ----------------

    /** 同步状态：指定 logId 或最近一次（运行中进度 / 最近结果） */
    public WecomSyncLog getSyncStatus(Long logId) {
        if (logId != null) {
            WecomSyncLog logEntity = wecomSyncLogMapper.selectById(logId);
            if (logEntity == null) {
                throw new BusinessException("同步记录不存在");
            }
            return logEntity;
        }
        return wecomSyncLogMapper.selectOne(new LambdaQueryWrapper<WecomSyncLog>()
                .orderByDesc(WecomSyncLog::getId)
                .last("LIMIT 1"));
    }

    /** 最近同步记录（管理页表格） */
    public List<WecomSyncLog> recentLogs(int limit) {
        return wecomSyncLogMapper.selectList(new LambdaQueryWrapper<WecomSyncLog>()
                .orderByDesc(WecomSyncLog::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    /** 部门树（含每部门主部门成员数与成员卡；企微已删除部门保留 status=0 节点） */
    public List<DepartmentNodeVo> getDepartmentTree() {
        List<WecomDepartment> depts = wecomDepartmentMapper.selectList(
                new LambdaQueryWrapper<WecomDepartment>().orderByAsc(WecomDepartment::getSortOrder));
        if (depts.isEmpty()) {
            return new ArrayList<>();
        }
        List<WecomUserDepartment> bindings = wecomUserDepartmentMapper.selectList(
                new LambdaQueryWrapper<WecomUserDepartment>().eq(WecomUserDepartment::getIsMain, 1));
        Map<Long, List<WecomUserDepartment>> byDept = bindings.stream()
                .collect(Collectors.groupingBy(WecomUserDepartment::getDeptId));
        Set<Long> userIds = bindings.stream().map(WecomUserDepartment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        Map<Long, DepartmentNodeVo> nodeMap = new LinkedHashMap<>();
        for (WecomDepartment d : depts) {
            DepartmentNodeVo node = new DepartmentNodeVo();
            node.setDeptId(d.getDeptId());
            node.setName(d.getName());
            node.setParentDeptId(d.getParentDeptId());
            node.setSortOrder(d.getSortOrder());
            node.setStatus(d.getStatus());
            List<WecomUserDepartment> members = byDept.getOrDefault(d.getDeptId(), List.of());
            node.setMemberCount(members.size());
            for (WecomUserDepartment wud : members) {
                SysUser u = userMap.get(wud.getUserId());
                if (u == null) {
                    continue;
                }
                DepartmentNodeVo.MemberItem mi = new DepartmentNodeVo.MemberItem();
                mi.setUserId(u.getId());
                mi.setUsername(u.getUsername());
                mi.setMobileMasked(WecomApiClient.maskMobile(u.getMobile()));
                mi.setIsMain(true);
                mi.setStatus(u.getStatus());
                node.getMembers().add(mi);
            }
            nodeMap.put(d.getDeptId(), node);
        }
        List<DepartmentNodeVo> roots = new ArrayList<>();
        for (DepartmentNodeVo node : nodeMap.values()) {
            if (node.getParentDeptId() != null && nodeMap.containsKey(node.getParentDeptId())) {
                nodeMap.get(node.getParentDeptId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /** 未绑定清单：系统未绑定存量用户 + 企微侧未匹配成员（最近一次同步 UNBOUND/IGNORED 项） */
    public UnboundDataVo getUnboundList() {
        UnboundDataVo vo = new UnboundDataVo();
        vo.setSystemUsers(sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .and(w -> w.isNull(SysUser::getWecomUserId).or().eq(SysUser::getWecomUserId, ""))
                .orderByAsc(SysUser::getId)));
        for (Map<String, Object> m : latestUnboundFromLog()) {
            UnboundDataVo.WecomMemberItem item = new UnboundDataVo.WecomMemberItem();
            item.setUserId(String.valueOf(m.getOrDefault("wecomUserId", "")));
            item.setName(String.valueOf(m.getOrDefault("name", "")));
            item.setMobileMasked(String.valueOf(m.getOrDefault("mobileMasked", "")));
            item.setReason(String.valueOf(m.getOrDefault("reason", "")));
            vo.getWecomMembers().add(item);
        }
        return vo;
    }

    /** 普通员工个人通讯录：根到本部门链路 + 本部门成员（手机号脱敏；未绑定/无部门返回空结构） */
    public Map<String, Object> getMyDirectory(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<DepartmentNodeVo> ancestors = new ArrayList<>();
        List<DepartmentNodeVo.MemberItem> members = new ArrayList<>();
        result.put("ancestors", ancestors);
        result.put("members", members);
        if (userId == null || sysUserMapper.selectById(userId) == null) {
            return result;
        }
        WecomUserDepartment main = wecomUserDepartmentMapper.selectOne(
                new LambdaQueryWrapper<WecomUserDepartment>()
                        .eq(WecomUserDepartment::getUserId, userId)
                        .eq(WecomUserDepartment::getIsMain, 1)
                        .last("LIMIT 1"));
        if (main == null) {
            return result;
        }
        Map<Long, WecomDepartment> byId = wecomDepartmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(WecomDepartment::getDeptId, d -> d));
        Long cur = main.getDeptId();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        while (cur != null && byId.containsKey(cur) && seen.add(cur)) {
            WecomDepartment d = byId.get(cur);
            DepartmentNodeVo node = new DepartmentNodeVo();
            node.setDeptId(d.getDeptId());
            node.setName(d.getName());
            node.setParentDeptId(d.getParentDeptId());
            node.setSortOrder(d.getSortOrder());
            node.setStatus(d.getStatus());
            ancestors.add(0, node);
            cur = d.getParentDeptId();
        }
        List<WecomUserDepartment> sameDept = wecomUserDepartmentMapper.selectList(
                new LambdaQueryWrapper<WecomUserDepartment>()
                        .eq(WecomUserDepartment::getDeptId, main.getDeptId())
                        .eq(WecomUserDepartment::getIsMain, 1));
        Set<Long> ids = sameDept.stream().map(WecomUserDepartment::getUserId).collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            Map<Long, SysUser> umap = sysUserMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u));
            for (WecomUserDepartment wud : sameDept) {
                SysUser u = umap.get(wud.getUserId());
                if (u == null || u.getStatus() == null || u.getStatus() == 0) {
                    continue;
                }
                DepartmentNodeVo.MemberItem mi = new DepartmentNodeVo.MemberItem();
                mi.setUserId(u.getId());
                mi.setUsername(u.getUsername());
                mi.setMobileMasked(WecomApiClient.maskMobile(u.getMobile()));
                mi.setIsMain(true);
                mi.setStatus(u.getStatus());
                members.add(mi);
            }
        }
        result.put("ancestors", ancestors);
        result.put("members", members);
        return result;
    }
}
