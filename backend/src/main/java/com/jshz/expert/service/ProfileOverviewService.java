package com.jshz.expert.service;

import com.jshz.expert.client.WecomApiClient;
import com.jshz.expert.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 我的主页：手动组织树、部门负责人、总经理任命与本人账号活动。
 * 组织树以「总经理」为唯一根节点，根节点负责人即总经理；任命在树行锁下串行执行，保证全系统只有一人。
 * 总经理同时获得 GENERAL_MANAGER 角色绑定，权限由 {@link PermissionService} 计算。
 */
@Service
@RequiredArgsConstructor
public class ProfileOverviewService {
    private static final int PAGE_SIZE = 20;
    private static final int ACTIVITY_DAYS = 30;
    private static final int ORGANIZATION_TEXT_LIMIT = 100;
    private static final String ROOT_SEED = "GENERAL_MANAGER";
    private static final String GENERAL_OFFICE_SEED = "GENERAL_OFFICE";
    public static final String USER_ACCOUNT = "USER_ACCOUNT";
    private static final String DEPARTMENTS =
            "SELECT id,parent_id,name,active,seed_key,leader_user_id FROM profile_department ORDER BY id";
    private static final RowMapper<Node> NODE_ROW = (rs, n) -> new Node(rs.getLong("id"),
            rs.getObject("parent_id", Long.class), rs.getString("name"), rs.getBoolean("active"),
            rs.getString("seed_key"), rs.getObject("leader_user_id", Long.class), null);

    private final JdbcTemplate jdbc;
    private final PermissionService permissionService;
    private final UserAuthStateService userAuthStateService;
    private final OperationLogService logs;
    /** 调岗后项目 team 重算（FR-05，2026-11-26 项目日历需求；仅引用，不形成反向依赖）。 */
    private final ProjectTeamSyncService teamSync;

    /** 组织树内部节点；leaderName 仅在负责人账号启用时非空（停用账号视为无负责人）。 */
    private record Node(Long id, Long parentId, String name, boolean active, String seedKey,
                        Long leaderUserId, String leaderName) {}

    public record Department(Long id, Long parentId, String name, boolean active, Long leaderUserId, String leaderName,
                             boolean generalManagerNode, boolean generalOfficeNode) {}
    public record DepartmentForm(Long parentId, String name, boolean active, Long leaderUserId) {}
    public record Membership(Long departmentId, String position) {}
    public record LedDepartment(Long id, String path) {}
    public record Member(Long id, String username, boolean active, Long departmentId, String departmentPath,
                         String position, boolean generalManager, List<String> leaderOf) {}
    public record Organization(String departmentPath, String position, String leaderName, boolean generalManager,
                               List<LedDepartment> ledDepartments) {}
    public record ProfilePage<T>(long total, List<T> items, int pageSize) {}
    public record Activity(String event, LocalDateTime occurredAt) {}

    public List<Department> departments() {
        List<Node> tree = tree(false);
        return tree.stream().map(node -> toDepartment(tree, node)).collect(Collectors.toList());
    }

    /** 本人组织信息：部门全路径、岗位、最近的上级负责人，以及本人负责的部门（不含总经理根节点）。 */
    public Organization organization(long userId) {
        List<Node> tree = tree(false);
        List<LedDepartment> led = new ArrayList<>();
        for (Node node : tree) {
            if (leads(node, userId) && !ROOT_SEED.equals(node.seedKey())) {
                led.add(new LedDepartment(node.id(), path(tree, node.id())));
            }
        }
        Node root = rootOrNull(tree);
        if (root != null && leads(root, userId)) {
            return new Organization(root.name(), null, null, true, led);
        }
        Membership membership;
        try {
            membership = jdbc.queryForObject("SELECT department_id,position FROM profile_membership WHERE user_id=?",
                    (rs, n) -> new Membership(rs.getLong(1), rs.getString(2)), userId);
        } catch (EmptyResultDataAccessException e) {
            return new Organization(null, null, null, false, led);
        }
        Node node = find(tree, membership.departmentId());
        if (node == null) {
            return new Organization(null, membership.position(), null, false, led);
        }
        String leaderName = null;
        Set<Long> visited = new HashSet<>();
        for (Node current = node; current != null && visited.add(current.id()); current = find(tree, current.parentId())) {
            if (current.leaderName() != null) {
                leaderName = current.leaderName();
                break;
            }
        }
        return new Organization(path(tree, node.id()), membership.position(), leaderName, false, led);
    }

    /**
     * 通讯录兜底数据：企微未绑定/未同步部门时读取手动组织树（profile_membership 链路 + 同部门成员）。
     * 响应结构与 ContactSyncService.getMyDirectory 对齐（ancestors/members/truncated），
     * 另附 source=profile 供前端展示数据来源提示；手机号同样脱敏（WecomApiClient.maskMobile，禁止复制本地副本）。
     * 仅返回本人部门链路与同部门成员，不暴露全量组织架构；未建档返回空结构。
     */
    public Map<String, Object> directoryFallback(long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> ancestors = new ArrayList<>();
        List<Map<String, Object>> members = new ArrayList<>();
        result.put("ancestors", ancestors);
        result.put("members", members);
        result.put("truncated", false);
        result.put("source", "profile");
        Membership membership = null;
        try {
            membership = jdbc.queryForObject("SELECT department_id,position FROM profile_membership WHERE user_id=?",
                    (rs, n) -> new Membership(rs.getLong(1), rs.getString(2)), userId);
        } catch (EmptyResultDataAccessException e) {
            // 无手动组织树归属 → 返回空结构，由前端展示未绑定部门空态
        }
        if (membership == null) {
            return result;
        }
        List<Node> tree = tree(false);
        Map<Long, Node> byId = new HashMap<>();
        for (Node node : tree) {
            byId.putIfAbsent(node.id(), node);
        }
        Long cur = membership.departmentId();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        boolean truncated = false;
        while (cur != null) {
            if (!seen.add(cur)) {
                truncated = true; // 父指针成环，链路截断
                break;
            }
            Node node = byId.get(cur);
            if (node == null) {
                truncated = true; // 部门节点缺失，链路截断
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deptId", node.id());
            item.put("name", node.name());
            ancestors.add(0, item);
            cur = node.parentId();
        }
        members.addAll(jdbc.query(
                "SELECT m.user_id,u.username,u.mobile,u.status,m.position FROM profile_membership m "
                        + "JOIN sys_user u ON u.id=m.user_id WHERE m.department_id=? ORDER BY m.user_id",
                (rs, n) -> {
                    Map<String, Object> mi = new LinkedHashMap<>();
                    mi.put("userId", rs.getLong(1));
                    mi.put("username", rs.getString(2));
                    mi.put("mobileMasked", WecomApiClient.maskMobile(rs.getString(3)));
                    mi.put("status", rs.getInt(4));
                    mi.put("position", rs.getString(5));
                    mi.put("isMain", true);
                    return mi;
                }, membership.departmentId()));
        result.put("truncated", truncated);
        return result;
    }

    /**
     * 新增或修改部门，返回负责人变动的审计补充说明（无变动返回 null）。
     * 根节点即总经理任命：换人直接顶替，新总经理清除部门归属并绑定 GENERAL_MANAGER 角色。
     */
    @Transactional
    public String saveDepartment(Long id, DepartmentForm form) {
        String name = requireText(form.name(), "部门名称");
        List<Node> tree = tree(true);
        Node current = find(tree, id);
        Node parent = find(tree, form.parentId());
        if (id != null && current == null) throw new BusinessException("部门不存在");
        boolean isRoot = current != null && ROOT_SEED.equals(current.seedKey());
        if (isRoot) {
            if (form.parentId() != null) throw new BusinessException("总经理必须保持在组织树顶层");
        } else if (form.parentId() == null) {
            throw new BusinessException("请选择上级部门");
        }
        if (form.parentId() != null && (parent == null || (form.active() && !parent.active()))) {
            throw new BusinessException("请选择有效上级部门");
        }
        var visited = new HashSet<Long>();
        for (Node node = parent; node != null; node = find(tree, node.parentId())) {
            if (Objects.equals(node.id(), id) || !visited.add(node.id())) {
                throw new BusinessException("部门不能移动到自身或其下级");
            }
        }
        if (tree.stream().anyMatch(node -> !Objects.equals(node.id(), id)
                && Objects.equals(node.parentId(), form.parentId()) && node.name().equalsIgnoreCase(name))) {
            throw new BusinessException("同一上级下已有同名部门");
        }
        if (!form.active() && current != null) {
            if (tree.stream().anyMatch(node -> id.equals(node.parentId()))
                    || jdbc.queryForObject("SELECT COUNT(*) FROM profile_membership WHERE department_id=?", Long.class, id) > 0) {
                throw new BusinessException("请先调整下级部门和成员归属");
            }
            if (current.leaderUserId() != null) throw new BusinessException("请先取消部门负责人再停用");
        }

        Long leaderUserId = form.leaderUserId();
        // 负责人同时是本部门成员：无主部门时自动把该部门设为其主部门（已有归属的人不动）
        boolean autoJoin = leaderUserId != null && !isRoot && !leadsRaw(root(tree), leaderUserId)
                && jdbc.queryForObject("SELECT COUNT(*) FROM profile_membership WHERE user_id=?",
                        Long.class, leaderUserId) == 0;
        String note = null;
        if (isRoot) {
            if (!Objects.equals(current.leaderUserId(), leaderUserId)) {
                String leaderName = leaderUserId == null ? null : activeUsername(leaderUserId);
                String previous = current.leaderName() == null ? String.valueOf(current.leaderUserId()) : current.leaderName();
                changeGeneralManager(tree, leaderUserId);
                note = leaderUserId == null ? "免去总经理「" + previous + "」" : "任命总经理「" + leaderName + "」";
            }
        } else if (GENERAL_OFFICE_SEED.equals(current == null ? null : current.seedKey())) {
            if (leaderUserId != null) throw new BusinessException("总经办负责人固定为总经理，无需单独设置");
        } else if (leaderUserId != null) {
            String leaderName = activeUsername(leaderUserId);
            if (!Objects.equals(current == null ? null : current.leaderUserId(), leaderUserId)) {
                note = "任命「" + name + "」负责人「" + leaderName + "」";
            }
        } else if (current != null && current.leaderUserId() != null) {
            note = "免去「" + name + "」负责人";
        }
        Long departmentId = id;
        if (id == null) {
            org.springframework.jdbc.support.GeneratedKeyHolder keys =
                    new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO profile_department (name,parent_id,active,leader_user_id) VALUES (?,?,?,?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                if (form.parentId() == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, form.parentId());
                ps.setBoolean(3, form.active());
                if (leaderUserId == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, leaderUserId);
                return ps;
            }, keys);
            departmentId = keys.getKey().longValue();
        } else {
            jdbc.update("UPDATE profile_department SET name=?,parent_id=?,active=?,leader_user_id=? WHERE id=?",
                    name, form.parentId(), form.active(), leaderUserId, id);
        }
        if (autoJoin) {
            jdbc.update("INSERT INTO profile_membership (user_id,department_id,position) VALUES (?,?,?)",
                    leaderUserId, departmentId, null);
        }
        return note;
    }

    /**
     * 成员列表。组织维护人（超管、总经理）可见全部；部门负责人仅可见本人负责部门及其全部下级的成员；
     * 其余账号拒绝。departmentId 用于按部门（含下级）筛选。
     */
    public ProfilePage<Member> members(boolean organizationAdmin, long viewerId, String keyword, int page,
                                       Long departmentId) {
        List<Node> tree = tree(false);
        Set<Long> scope = null;
        if (!organizationAdmin) {
            scope = new LinkedHashSet<>();
            for (Node node : tree) {
                if (leads(node, viewerId)) scope.addAll(subtree(tree, node.id()));
            }
            if (scope.isEmpty()) throw new BusinessException(403, "仅部门负责人可查看部门成员");
        }
        if (departmentId != null) {
            Set<Long> filter = subtree(tree, departmentId);
            if (filter.isEmpty()) throw new BusinessException("部门不存在");
            if (scope == null) scope = filter; else scope.retainAll(filter);
            if (scope.isEmpty()) throw new BusinessException(403, "只能查看本人负责部门及其下级的成员");
        }
        StringBuilder where = new StringBuilder(
                " FROM sys_user u LEFT JOIN profile_membership m ON m.user_id=u.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND u.username LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (scope != null) {
            where.append(" AND m.department_id IN (").append(String.join(",", Collections.nCopies(scope.size(), "?"))).append(")");
            args.addAll(scope);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(PAGE_SIZE);
        rowArgs.add(offset(page));
        List<Member> records = jdbc.query("""
                SELECT u.id,u.username,u.status,m.department_id,m.position
                """ + where + " ORDER BY u.id LIMIT ? OFFSET ?",
                (rs, n) -> new Member(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1, rs.getObject(4, Long.class),
                        null, rs.getString(5), false, List.of()), rowArgs.toArray());
        Map<Long, List<String>> leaderOf = new HashMap<>();
        Node root = rootOrNull(tree);
        for (Node node : tree) {
            if (node.leaderUserId() == null || node.leaderName() == null || (root != null && root.id().equals(node.id()))) continue;
            leaderOf.computeIfAbsent(node.leaderUserId(), key -> new ArrayList<>()).add(path(tree, node.id()));
        }
        List<Member> items = records.stream().map(member -> new Member(member.id(), member.username(), member.active(),
                member.departmentId(), root != null && leads(root, member.id()) ? root.name()
                        : member.departmentId() == null ? null : path(tree, member.departmentId()),
                member.position(), root != null && leads(root, member.id()),
                leaderOf.getOrDefault(member.id(), List.of()))).collect(Collectors.toList());
        return new ProfilePage<>(count, items, PAGE_SIZE);
    }

    @Transactional
    public void saveMembership(long userId, Membership form) {
        List<Node> tree = tree(true);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=?", Long.class, userId) == 0) {
            throw new BusinessException("账号不存在");
        }
        if (leads(root(tree), userId)) throw new BusinessException("总经理不设置部门与岗位，可代理其他部门负责人");
        if (form.departmentId() == null) {
            jdbc.update("DELETE FROM profile_membership WHERE user_id=?", userId);
            resyncProjectsAfterCommit(userId);
            return;
        }
        Node target = find(tree, form.departmentId());
        if (target == null || !target.active()) throw new BusinessException("请选择启用的部门");
        String position = form.position() == null || form.position().isBlank() ? null : requireText(form.position(), "岗位");
        jdbc.update("""
                INSERT INTO profile_membership (user_id,department_id,position) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE department_id=?,position=?
                """, userId, target.id(), position, target.id(), position);
        // 调岗钩子（FR-05，2026-11-26 项目日历需求）：提交后对该员工名下 ACTIVE 项目的 team 重算
        resyncProjectsAfterCommit(userId);
    }

    /** 事务提交后重算该员工名下 ACTIVE 项目 team（无事务时直接执行）。 */
    private void resyncProjectsAfterCommit(long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            teamSync.resyncForUser(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                teamSync.resyncForUser(userId);
            }
        });
    }

    /** 账号停用或删除时调用：自动撤销其全部负责人身份；若为总经理，同时免去总经理并回收角色。 */
    @Transactional
    public void revokeLeadership(long userId, String reason) {
        List<Node> tree = tree(true);
        List<String> names = tree.stream().filter(node -> leadsRaw(node, userId))
                .map(node -> path(tree, node.id())).collect(Collectors.toList());
        if (names.isEmpty()) return;
        if (leadsRaw(root(tree), userId)) changeGeneralManager(tree, null);
        jdbc.update("UPDATE profile_department SET leader_user_id=NULL WHERE leader_user_id=?", userId);
        logs.record(null, null, "PROFILE_ORG_LEADER", reason + "：" + String.join("、", names), USER_ACCOUNT, userId, null);
    }

    /** GENERAL_MANAGER 角色主键；角色尚未建档时返回 null。 */
    public Long generalManagerRoleId() {
        List<Long> ids = jdbc.query("SELECT id FROM role WHERE code=?",
                (rs, n) -> rs.getLong(1), PermissionService.GENERAL_MANAGER_CODE);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public ProfilePage<Activity> activity(long userId, int page) {
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVITY_DAYS);
        String where = """
                 FROM operation_log WHERE create_time >= ? AND (
                   (user_id = ? AND action_type IN (?,?,?))
                   OR (action_type = ? AND target_type = ? AND target_id = ?)
                 )
                """;
        Object[] args = {since, userId, OperationLogService.ACTION_LOGIN, OperationLogService.ACTION_LOGOUT,
                OperationLogService.ACTION_CHANGE_PASSWORD, OperationLogService.ACTION_RESET_PASSWORD, USER_ACCOUNT, String.valueOf(userId)};
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args);
        List<Activity> rows = jdbc.query(
                "SELECT action_type,create_time" + where + " ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new Activity(rs.getString(1), rs.getTimestamp(2).toLocalDateTime()),
                since, userId, OperationLogService.ACTION_LOGIN, OperationLogService.ACTION_LOGOUT,
                OperationLogService.ACTION_CHANGE_PASSWORD, OperationLogService.ACTION_RESET_PASSWORD,
                USER_ACCOUNT, String.valueOf(userId), PAGE_SIZE, offset(page));
        return new ProfilePage<>(count, rows, PAGE_SIZE);
    }

    /**
     * 任命（userId 为 null 表示免去）总经理。调用方必须已持有组织树行锁。
     * 新总经理清除部门归属并绑定角色，前任解绑；相关账号的登录态在事务提交后失效，需重新登录取得新权限。
     */
    private void changeGeneralManager(List<Node> tree, Long userId) {
        Node root = root(tree);
        Long previous = root.leaderUserId();
        Long roleId = generalManagerRoleId();
        if (roleId == null) throw new BusinessException("缺少总经理角色，请先执行组织架构迁移");
        if (userId != null) {
            jdbc.update("DELETE FROM profile_membership WHERE user_id=?", userId);
            jdbc.update("INSERT IGNORE INTO user_role (user_id,role_id) VALUES (?,?)", userId, roleId);
        }
        if (previous != null) {
            jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", previous, roleId);
        }
        if (userId != null) refreshAfterCommit(userId);
        if (previous != null && !previous.equals(userId)) refreshAfterCommit(previous);
    }

    private void refreshAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            userAuthStateService.bumpAndEvict(userId);
            permissionService.clearCaches();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userAuthStateService.bumpAndEvict(userId);
                permissionService.clearCaches();
            }
        });
    }

    /** 读取组织树并补全负责人姓名；lock=true 时加行锁，用于全部写操作。 */
    private List<Node> tree(boolean lock) {
        List<Node> tree = jdbc.query(DEPARTMENTS + (lock ? " FOR UPDATE" : ""), NODE_ROW);
        List<Long> leaderIds = tree.stream().map(Node::leaderUserId).filter(Objects::nonNull).distinct().toList();
        if (leaderIds.isEmpty()) return tree;
        // RowCallbackHandler 每行回调一次且游标已定位，回调内不得再调用 rs.next()
        Map<Long, String> names = new HashMap<>();
        jdbc.query("SELECT id,username FROM sys_user WHERE status=1 AND id IN ("
                        + String.join(",", Collections.nCopies(leaderIds.size(), "?")) + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> names.put(rs.getLong(1), rs.getString(2)),
                leaderIds.toArray());
        return tree.stream().map(node -> new Node(node.id(), node.parentId(), node.name(), node.active(), node.seedKey(),
                node.leaderUserId(), names.get(node.leaderUserId()))).collect(Collectors.toList());
    }

    private String activeUsername(Long userId) {
        List<String> names = jdbc.query("SELECT username FROM sys_user WHERE id=? AND status=1",
                (rs, n) -> rs.getString(1), userId);
        if (names.isEmpty()) throw new BusinessException("负责人必须是启用的账号");
        return names.get(0);
    }

    private static Department toDepartment(List<Node> tree, Node node) {
        Node root = rootOrNull(tree);
        boolean office = GENERAL_OFFICE_SEED.equals(node.seedKey());
        Node holder = office ? root : node;
        return new Department(node.id(), node.parentId(), node.name(), node.active(),
                holder == null ? null : holder.leaderUserId(), holder == null ? null : holder.leaderName(),
                root != null && root.id().equals(node.id()), office);
    }

    private static Node root(List<Node> tree) {
        Node root = rootOrNull(tree);
        if (root == null) throw new BusinessException("组织树缺少总经理节点，请先执行组织架构迁移");
        return root;
    }

    private static Node rootOrNull(List<Node> tree) {
        return tree.stream().filter(node -> ROOT_SEED.equals(node.seedKey())).findFirst().orElse(null);
    }

    /** 该节点负责人为 userId 且账号启用。 */
    private static boolean leads(Node node, long userId) {
        return node.leaderName() != null && leadsRaw(node, userId);
    }

    private static boolean leadsRaw(Node node, long userId) {
        return node.leaderUserId() != null && node.leaderUserId() == userId;
    }

    /** 从总经理根节点到本部门的完整路径。 */
    private static String path(List<Node> tree, Long id) {
        List<String> names = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (Node node = find(tree, id); node != null && visited.add(node.id()); node = find(tree, node.parentId())) {
            names.add(0, node.name());
        }
        return String.join(" / ", names);
    }

    /** 本部门及其全部下级。 */
    private static Set<Long> subtree(List<Node> tree, Long id) {
        Set<Long> ids = new LinkedHashSet<>();
        if (find(tree, id) == null) return ids;
        ids.add(id);
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Node node : tree) {
                if (ids.contains(node.parentId()) && ids.add(node.id())) grew = true;
            }
        }
        return ids;
    }

    private static long offset(int page) {
        return (Math.max(page, 1) - 1L) * PAGE_SIZE;
    }

    private static Node find(List<Node> tree, Long id) {
        if (id == null) return null;
        return tree.stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    private static String requireText(String raw, String label) {
        if (raw == null || raw.isBlank() || raw.trim().length() > ORGANIZATION_TEXT_LIMIT) {
            throw new BusinessException(label + "需填写，且不能超过 " + ORGANIZATION_TEXT_LIMIT + " 字");
        }
        return raw.trim();
    }
}
