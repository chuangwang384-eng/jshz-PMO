package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProfileOverviewService {
    private static final int PAGE_SIZE = 20;
    private static final int ACTIVITY_DAYS = 30;
    private static final int ORGANIZATION_TEXT_LIMIT = 100;
    public static final String USER_ACCOUNT = "USER_ACCOUNT";
    private static final String DEPARTMENTS = "SELECT id,parent_id,name,active FROM profile_department ORDER BY id";
    private static final RowMapper<Department> DEPARTMENT_ROW = (rs, n) -> new Department(rs.getLong("id"),
            rs.getObject("parent_id", Long.class), rs.getString("name"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public record Department(Long id, Long parentId, String name, boolean active) {}
    public record DepartmentForm(Long parentId, String name, boolean active) {}
    public record Membership(Long departmentId, String position) {}
    public record Member(Long id, String username, boolean active, Long departmentId, String position) {}
    public record Organization(String departmentPath, String position) {}
    public record ProfilePage<T>(long total, List<T> items, int pageSize) {}
    public record Activity(String event, LocalDateTime occurredAt) {}

    public List<Department> departments() {
        return jdbc.query(DEPARTMENTS, DEPARTMENT_ROW);
    }

    public Organization organization(long userId) {
        Membership membership;
        try {
            membership = jdbc.queryForObject("SELECT department_id,position FROM profile_membership WHERE user_id=?",
                    (rs, n) -> new Membership(rs.getLong(1), rs.getString(2)), userId);
        } catch (EmptyResultDataAccessException e) {
            return new Organization(null, null);
        }
        List<Department> tree = departments();
        List<String> path = new ArrayList<>();
        var visited = new HashSet<Long>();
        for (Long id = membership.departmentId(); id != null && visited.add(id); ) {
            Department node = find(tree, id);
            if (node == null) break;
            path.add(0, node.name());
            id = node.parentId();
        }
        return new Organization(String.join(" / ", path), membership.position());
    }

    @Transactional
    public void saveDepartment(Long id, DepartmentForm form) {
        String name = requireText(form.name(), "部门名称");
        List<Department> tree = jdbc.query(DEPARTMENTS + " FOR UPDATE", DEPARTMENT_ROW);
        Department current = find(tree, id);
        Department parent = find(tree, form.parentId());
        if (id != null && current == null) throw new BusinessException("部门不存在");
        if (form.parentId() != null && (parent == null || (form.active() && !parent.active()))) {
            throw new BusinessException("请选择有效上级部门");
        }
        var visited = new HashSet<Long>();
        for (Department node = parent; node != null; node = find(tree, node.parentId())) {
            if (Objects.equals(node.id(), id) || !visited.add(node.id())) {
                throw new BusinessException("部门不能移动到自身或其下级");
            }
        }
        if (tree.stream().anyMatch(node -> !Objects.equals(node.id(), id)
                && Objects.equals(node.parentId(), form.parentId()) && node.name().equalsIgnoreCase(name))) {
            throw new BusinessException("同一上级下已有同名部门");
        }
        if (!form.active() && id != null && (tree.stream().anyMatch(node -> id.equals(node.parentId()))
                || jdbc.queryForObject("SELECT COUNT(*) FROM profile_membership WHERE department_id=?", Long.class, id) > 0)) {
            throw new BusinessException("请先调整下级部门和成员归属");
        }
        if (id == null) {
            jdbc.update("INSERT INTO profile_department (name,parent_id,active) VALUES (?,?,?)", name, form.parentId(), form.active());
        } else {
            jdbc.update("UPDATE profile_department SET name=?,parent_id=?,active=? WHERE id=?", name, form.parentId(), form.active(), id);
        }
    }

    public ProfilePage<Member> members(String keyword, int page) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE ?", Long.class, like);
        List<Member> records = jdbc.query("""
                SELECT u.id,u.username,u.status,m.department_id,m.position
                FROM sys_user u LEFT JOIN profile_membership m ON m.user_id=u.id
                WHERE u.username LIKE ? ORDER BY u.id LIMIT ? OFFSET ?
                """, (rs, n) -> new Member(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1,
                rs.getObject(4, Long.class), rs.getString(5)), like, PAGE_SIZE, offset(page));
        return new ProfilePage<>(count, records, PAGE_SIZE);
    }

    @Transactional
    public void saveMembership(long userId, Membership form) {
        List<Department> tree = jdbc.query(DEPARTMENTS + " FOR UPDATE", DEPARTMENT_ROW);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=?", Long.class, userId) == 0) {
            throw new BusinessException("账号不存在");
        }
        if (form.departmentId() == null) {
            jdbc.update("DELETE FROM profile_membership WHERE user_id=?", userId);
            return;
        }
        Department node = find(tree, form.departmentId());
        if (node == null || !node.active()) throw new BusinessException("请选择启用的部门");
        String position = form.position() == null || form.position().isBlank() ? null : requireText(form.position(), "岗位");
        jdbc.update("""
                INSERT INTO profile_membership (user_id,department_id,position) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE department_id=?,position=?
                """, userId, node.id(), position, node.id(), position);
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

    private static long offset(int page) {
        return (Math.max(page, 1) - 1L) * PAGE_SIZE;
    }

    private static Department find(List<Department> tree, Long id) {
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
