package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目日历服务（2026-11-26 项目日历需求）：
 * - 个人会议：负责人/协助人/历史执行人三类角色（执行人表已停用写入，老数据兼容，需求 C1）；
 * - 部门排期：仅 profile_department.leader_user_id 命中者可用，范围=本部门及全部下级；
 *   总经理（GENERAL_MANAGER 根节点负责人）可见全员（8.1 决策）；
 * - 字段白名单：只返回日期、项目名、负责人、角色（部门范围另含成员标识与 disabled），
 *   不返回预算/客户/专家/阶段/文件等任何字段——日历可见范围大于详情可见范围是需求 C6 的明确接受项；
 * - 过滤：status=ACTIVE、会议日期非空、与 [from,to] 区间重叠；已取消/已删除不出现（FR-06）；
 *   已停用账号在部门排期保留并标 disabled（8.6 决策）。
 * 后端聚合（deptMeetings 卡片撞期计数）以本服务为唯一实现；前端月历/排期展示逻辑在
 * frontend/src/utils/projectCalendar.js，两侧不复制对方逻辑。
 */
@Service
@RequiredArgsConstructor
public class ProjectCalendarService {

    public static final ZoneId ZONE = DashboardReadService.ZONE;
    /** 单次查询最大区间跨度（自然日）。 */
    public static final int MAX_RANGE_DAYS = 62;
    /** 工作台「我的近期会议」窗口（FR-03）。 */
    public static final int MY_MEETING_DAYS = 14;
    /** 工作台「本部门概览」撞期窗口（FR-03）。 */
    public static final int DEPT_WINDOW_DAYS = 14;
    /** 「本部门概览」涉及会议数窗口。 */
    public static final int DEPT_WEEK_DAYS = 7;
    /** 卡片最多展示条数。 */
    public static final int CARD_LIMIT = 8;

    static final String ROOT_SEED = "GENERAL_MANAGER";
    static final String SQL_MEETINGS =
            "SELECT p.id, p.name, p.meeting_start_date, p.meeting_end_date, ou.username AS owner_name, r.user_id, r.role "
            + "FROM project p JOIN sys_user ou ON ou.id = p.owner_id JOIN ("
            + "  SELECT id AS project_id, owner_id AS user_id, 'OWNER' AS role FROM project"
            + "  UNION ALL SELECT project_id, user_id, 'ASSISTANT' FROM project_assistant"
            + "  UNION ALL SELECT project_id, user_id, 'EXECUTOR' FROM project_executor"
            + ") r ON r.project_id = p.id "
            + "WHERE p.status = 'ACTIVE' AND p.meeting_start_date IS NOT NULL AND p.meeting_end_date IS NOT NULL "
            + "AND p.meeting_start_date <= ? AND p.meeting_end_date >= ? AND r.user_id IN (%s)";

    private final JdbcTemplate db;

    /** 一条日历会议（字段白名单）。 */
    public static class Meeting {
        private final Long projectId;
        private final String name;
        private final String ownerName;
        private final LocalDate meetingStartDate;
        private final LocalDate meetingEndDate;
        /** 本条查询范围内命中的 (userId -> 角色列表)。 */
        private final Map<Long, List<String>> roleUsers = new LinkedHashMap<>();

        public Meeting(Long projectId, String name, String ownerName,
                       LocalDate meetingStartDate, LocalDate meetingEndDate) {
            this.projectId = projectId;
            this.name = name;
            this.ownerName = ownerName;
            this.meetingStartDate = meetingStartDate;
            this.meetingEndDate = meetingEndDate;
        }

        public Long projectId() { return projectId; }
        public String name() { return name; }
        public String ownerName() { return ownerName; }
        public LocalDate meetingStartDate() { return meetingStartDate; }
        public LocalDate meetingEndDate() { return meetingEndDate; }

        /** 指定用户在该项目中的角色集合（可含多角色）。 */
        public List<String> rolesOf(long userId) {
            return roleUsers.getOrDefault(userId, List.of());
        }

        /** 该项目在区间内命中的用户集合。 */
        public Set<Long> userIds() {
            return roleUsers.keySet();
        }

        void addRole(long userId, String role) {
            roleUsers.computeIfAbsent(userId, k -> new ArrayList<>()).add(role);
        }
    }

    /** 部门排期成员（白名单：id/姓名/停用标记 + 按天会议）。 */
    public static class Member {
        private final Long userId;
        private final String userName;
        private final boolean disabled;
        private final Map<LocalDate, List<Meeting>> byDay = new TreeMap<>();

        Member(Long userId, String userName, boolean disabled) {
            this.userId = userId;
            this.userName = userName;
            this.disabled = disabled;
        }

        public Long userId() { return userId; }
        public String userName() { return userName; }
        public boolean disabled() { return disabled; }
        public Map<LocalDate, List<Meeting>> byDay() { return byDay; }
    }

    public record DeptSchedule(List<Member> members) {}

    /**
     * 个人会议（scope=me）：当前用户作为三类角色参与的 ACTIVE 项目，区间重叠。
     */
    public List<Meeting> myMeetings(AuthUser user, LocalDate from, LocalDate to) {
        return queryForUsers(List.of(user.getUserId()), from, to);
    }

    /**
     * 部门排期（scope=dept）：调用人必须是部门负责人（或总经理），否则 403。
     * 部门范围由服务端计算，不接受前端传部门。
     */
    public DeptSchedule deptSchedule(AuthUser user, LocalDate from, LocalDate to) {
        List<Long> memberIds = managedUserIds(user);
        if (memberIds.isEmpty()) {
            throw new BusinessException(403, "仅部门负责人可查看部门排期");
        }
        List<Member> members = new ArrayList<>();
        Map<Long, Member> byId = new LinkedHashMap<>();
        db.query("SELECT u.id, u.username, u.status FROM sys_user u WHERE u.id IN ("
                        + placeholders(memberIds.size()) + ") ORDER BY u.id",
                rs -> {
                    Member m = new Member(rs.getLong(1), rs.getString(2), rs.getInt(3) != 1);
                    byId.put(m.userId(), m);
                    members.add(m);
                }, memberIds.toArray());
        for (Meeting meeting : queryForUsers(memberIds, from, to)) {
            for (LocalDate d = meeting.meetingStartDate(); !d.isAfter(meeting.meetingEndDate()); d = d.plusDays(1)) {
                if (d.isBefore(from) || d.isAfter(to)) {
                    continue;
                }
                for (Long uid : meeting.userIds()) {
                    Member m = byId.get(uid);
                    if (m != null) {
                        m.byDay().computeIfAbsent(d, k -> new ArrayList<>()).add(meeting);
                    }
                }
            }
        }
        return new DeptSchedule(members);
    }

    /**
     * 工作台「我的近期会议」卡片（FR-03）：未来 14 天，按开始日期排序，最多 8 条。
     */
    public Map<String, Object> myMeetingsSection(AuthUser user) {
        LocalDate today = LocalDate.now(ZONE);
        long uid = user.getUserId();
        List<Meeting> list = myMeetings(user, today, today.plusDays(MY_MEETING_DAYS));
        list.sort(Comparator.comparing(Meeting::meetingStartDate));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Meeting m : list) {
            if (items.size() >= CARD_LIMIT) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", m.projectId());
            item.put("name", m.name());
            item.put("ownerName", m.ownerName());
            item.put("meetingStartDate", m.meetingStartDate().toString());
            item.put("meetingEndDate", m.meetingEndDate().toString());
            item.put("roles", m.rolesOf(uid));
            item.put("target", DashboardRules.target("/project/" + m.projectId(), Map.of("from", "dashboard")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", list.size());
        data.put("items", items);
        data.put("moreThanShown", list.size() > items.size());
        return section(data, list.isEmpty());
    }

    /**
     * 工作台「本部门会议概览」卡片（FR-03）：未来 7 天涉及会议数、未来 14 天撞期明细。
     * 非部门负责人返回 null（Controller 省略该 section）。
     */
    public Map<String, Object> deptMeetingsSection(AuthUser user) {
        List<Long> memberIds = managedUserIds(user);
        if (memberIds.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now(ZONE);
        Map<Long, Member> nameById = new LinkedHashMap<>();
        db.query("SELECT id, username, status FROM sys_user WHERE id IN (" + placeholders(memberIds.size()) + ")",
                rs -> {
                    Member m = new Member(rs.getLong(1), rs.getString(2), rs.getInt(3) != 1);
                    nameById.put(m.userId(), m);
                }, memberIds.toArray());
        long weekCount = queryForUsers(memberIds, today, today.plusDays(DEPT_WEEK_DAYS)).stream()
                .map(Meeting::projectId).distinct().count();
        // 撞期：同一成员同一天 >=2 个项目（后端唯一实现处）；按日聚合后逐日判定，避免区间内重复查询
        Map<LocalDate, List<Meeting>> byDay = new TreeMap<>();
        for (Meeting meeting : queryForUsers(memberIds, today, today.plusDays(DEPT_WINDOW_DAYS))) {
            for (LocalDate d = meeting.meetingStartDate(); !d.isAfter(meeting.meetingEndDate()); d = d.plusDays(1)) {
                if (!d.isBefore(today) && !d.isAfter(today.plusDays(DEPT_WINDOW_DAYS))) {
                    byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(meeting);
                }
            }
        }
        List<Map<String, Object>> collisions = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Meeting>> day : byDay.entrySet()) {
            Map<Long, Long> perUser = new LinkedHashMap<>();
            for (Meeting m : day.getValue()) {
                for (Long uid : m.userIds()) {
                    perUser.merge(uid, 1L, Long::sum);
                }
            }
            for (Map.Entry<Long, Long> e : perUser.entrySet()) {
                if (e.getValue() >= 2) {
                    addCollision(collisions, nameById, e.getKey(), day.getKey());
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upcoming7Count", weekCount);
        data.put("collision14Count", collisions.size());
        data.put("collisions", collisions);
        return section(data, weekCount == 0 && collisions.isEmpty());
    }

    private void addCollision(List<Map<String, Object>> collisions, Map<Long, Member> nameById, Long uid, LocalDate d) {
        Member m = nameById.get(uid);
        if (m == null || collisions.stream().anyMatch(c -> c.get("userId").equals(uid) && c.get("date").equals(d.toString()))) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("userId", m.userId());
        item.put("userName", m.userName());
        item.put("disabled", m.disabled());
        item.put("date", d.toString());
        collisions.add(item);
    }

    // ==================== 部门范围（服务端计算，唯一实现处） ====================

    private record DeptNode(Long id, Long parentId, Long leaderUserId, String seedKey) {}

    /**
     * 调用人可管理的成员 userId 集合：部门负责人=本部门及全部下级成员（8.1）；
     * 总经理（根节点负责人）=全部部门成员；都不是返回空集。
     */
    List<Long> managedUserIds(AuthUser user) {
        long uid = user.getUserId();
        List<DeptNode> nodes = db.query(
                "SELECT id, parent_id, leader_user_id, seed_key FROM profile_department WHERE active = 1",
                (rs, n) -> new DeptNode(rs.getLong(1), rs.getObject(2, Long.class),
                        rs.getObject(3, Long.class), rs.getString(4)));
        boolean generalManager = nodes.stream()
                .anyMatch(n -> ROOT_SEED.equals(n.seedKey()) && n.leaderUserId() != null && n.leaderUserId() == uid);
        Set<Long> scopeDepts = new LinkedHashSet<>();
        if (generalManager) {
            nodes.forEach(n -> scopeDepts.add(n.id()));
        } else {
            Set<Long> ledRoots = nodes.stream()
                    .filter(n -> n.leaderUserId() != null && n.leaderUserId() == uid)
                    .map(DeptNode::id).collect(Collectors.toCollection(LinkedHashSet::new));
            for (Long root : ledRoots) {
                scopeDepts.add(root);
                for (boolean grew = true; grew; ) {
                    grew = false;
                    for (DeptNode n : nodes) {
                        if (n.parentId() != null && scopeDepts.contains(n.parentId()) && scopeDepts.add(n.id())) {
                            grew = true;
                        }
                    }
                }
            }
        }
        if (scopeDepts.isEmpty()) {
            return List.of();
        }
        org.springframework.jdbc.core.RowMapper<Long> userIdRow = (rs, n) -> rs.getLong(1);
        return db.query("SELECT user_id FROM profile_membership WHERE department_id IN ("
                        + placeholders(scopeDepts.size()) + ")",
                userIdRow, scopeDepts.toArray()).stream().distinct().collect(Collectors.toList());
    }

    // ==================== 查询 ====================

    private List<Meeting> queryForUsers(List<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<Meeting> meetings = new ArrayList<>();
        Map<Long, Meeting> byId = new LinkedHashMap<>();
        String sql = String.format(SQL_MEETINGS, placeholders(userIds.size()));
        db.query(sql, rs -> {
            long projectId = rs.getLong(1);
            Meeting m = byId.get(projectId);
            if (m == null) {
                Date start = rs.getDate(3);
                Date end = rs.getDate(4);
                m = new Meeting(projectId, rs.getString(2), rs.getString(5),
                        start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate());
                byId.put(projectId, m);
                meetings.add(m);
            }
            m.addRole(rs.getLong(6), rs.getString(7));
        }, to, from, userIds.toArray());
        return meetings;
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    /** 与 DashboardReadService.section 同构（status/data/updatedAt），供工作台卡片复用。 */
    private Map<String, Object> section(Map<String, Object> data, boolean empty) {
        return Map.of("status", empty ? "empty" : "success", "data", data,
                "updatedAt", OffsetDateTime.now(ZONE).toString());
    }

    /** 日期参数解析与校验：格式 YYYY-MM-DD、from<=to、跨度上限（设计 §5.1）。 */
    public static LocalDate[] parseRange(String from, String to) {
        LocalDate f;
        LocalDate t;
        try {
            f = LocalDate.parse(from);
            t = LocalDate.parse(to);
        } catch (Exception e) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
        if (t.isBefore(f)) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        if (f.plusDays(MAX_RANGE_DAYS).isBefore(t)) {
            throw new BusinessException("查询区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        return new LocalDate[]{f, t};
    }
}
