package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 项目日历服务单元测试（2026-11-26 项目日历需求）：
 * 覆盖范围计算（本部门+下级/总经理）、角色合并、三类角色口径、403 与区间校验。
 */
@ExtendWith(MockitoExtension.class)
class ProjectCalendarServiceTest {

    @Mock JdbcTemplate db;
    ProjectCalendarService service;
    /** 记录最近一次 db.query 的展开实参（varargs 绑定回归用）。 */
    Object[] lastQueryArgs;

    @BeforeEach
    void setUp() {
        service = new ProjectCalendarService(db);
    }

    AuthUser user(long id) {
        return new AuthUser(id, "fixture", false, false, Set.of(), Set.of());
    }

    // ---------- parseRange ----------

    @Test
    void parseRangeRejectsBadFormatAndReversedRange() {
        assertThrows(BusinessException.class, () -> ProjectCalendarService.parseRange("2026-1-1", "2026-01-31"));
        assertThrows(BusinessException.class, () -> ProjectCalendarService.parseRange("2026-01-31", "2026-01-01"));
        assertThrows(BusinessException.class,
                () -> ProjectCalendarService.parseRange("2026-01-01", "2026-04-01"),
                "超过 62 天应拒绝");
        LocalDate[] ok = ProjectCalendarService.parseRange("2026-12-01", "2026-12-31");
        assertEquals(LocalDate.of(2026, 12, 1), ok[0]);
    }

    // ---------- 部门范围 ----------

    private void mockDeptTree(boolean userIsManager, boolean userIsGeneralManager) {
        // 组织：总经理(root) → 项目部 → 项目一部/项目二部；负责人只挂项目一部
        Object[][] rows = {
                {1L, null, userIsGeneralManager ? (Object) 99L : null, "GENERAL_MANAGER"},
                {2L, 1L, null, "PROJECT"},
                {3L, 2L, userIsManager ? (Object) 99L : 7L, "PROJECT_1"},
                {4L, 2L, null, "PROJECT_2"}};
        doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (int i = 0; i < rows.length; i++) {
                Object[] r = rows[i];
                ResultSet rs = mock(ResultSet.class, withSettings().lenient());
                when(rs.getLong(1)).thenReturn((Long) r[0]);
                when(rs.getObject(2, Long.class)).thenReturn((Long) r[1]);
                when(rs.getObject(3, Long.class)).thenReturn((Long) r[2]);
                when(rs.getString(4)).thenReturn((String) r[3]);
                out.add(mapper.mapRow(rs, i));
            }
            return out;
        }).when(db).query(contains("profile_department"), any(RowMapper.class));
    }

    @Test
    void managerScopeCoversOwnDepartmentAndSubtree() {
        mockDeptTree(true, false);
        // 项目一部(id=3)成员 10/11、项目二部(id=4)成员 20 → 负责人只应看到 10/11
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            // Mockito 把 varargs 展开成逐个参数：下标 2 起为 department_id 占位符实参
            Object first = inv.getArgument(2);
            long deptId = ((Number) first).longValue();
            return (deptId == 3L ? List.of(10L, 11L)
                    : deptId == 4L ? List.of(20L) : List.of());
        });
        // 负责人只挂项目一部(id=3) → scope=子树{3}，成员 10/11；项目二部(id=4)的 20 不可见
        List<Long> ids = service.managedUserIds(user(99));
        assertTrue(ids.contains(10L) && ids.contains(11L), "本部门成员可见");
        assertFalse(ids.contains(20L), "平级部门成员不可见（组织树中项目二部是兄弟节点）");
    }

    @Test
    void generalManagerSeesAllDepartments() {
        mockDeptTree(false, true);
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> List.of(10L, 20L));
        List<Long> ids = service.managedUserIds(user(99));
        assertEquals(List.of(10L, 20L), ids);
    }

    @Test
    void plainEmployeeHasEmptyScope() {
        mockDeptTree(false, false);
        List<Long> ids = service.managedUserIds(user(99));
        assertTrue(ids.isEmpty());
    }

    @Test
    void deptScheduleRejectsNonManager() {
        mockDeptTree(false, false);
        assertThrows(BusinessException.class, () -> service.deptSchedule(user(99),
                LocalDate.now(), LocalDate.now().plusDays(7)));
    }

    // ---------- 查询与角色 ----------

    private void mockMeetingRows(long projectId, String name, String owner, LocalDate s, LocalDate e,
                                 Object[][] userRoles) {
        Deque<Object[][]> queue = new ArrayDeque<>();
        queue.add(userRoles);
        doAnswer(inv -> {
            lastQueryArgs = inv.getArguments();
            RowCallbackHandler handler = inv.getArgument(1);
            for (Object[] ur : queue.poll()) {
                ResultSet rs = mock(ResultSet.class, withSettings().lenient());
                when(rs.getLong(1)).thenReturn(projectId);
                when(rs.getString(2)).thenReturn(name);
                when(rs.getDate(3)).thenReturn(Date.valueOf(s));
                when(rs.getDate(4)).thenReturn(Date.valueOf(e));
                when(rs.getString(5)).thenReturn(owner);
                when(rs.getLong(6)).thenReturn((Long) ur[0]);
                when(rs.getString(7)).thenReturn((String) ur[1]);
                handler.processRow(rs);
            }
            return null;
        }).when(db).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void myMeetingsCoversThreeRolesAndMergesMultiRole() {
        // 一个项目同时是负责人+协助人 → roles 合并；另一项目是执行人
        mockMeetingRows(1L, "峰会A", "甲", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2),
                new Object[][] {{9L, "OWNER"}, {9L, "ASSISTANT"}});
        List<ProjectCalendarService.Meeting> first = service.myMeetings(user(9),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertEquals(1, first.size());
        assertEquals(List.of("OWNER", "ASSISTANT"), first.get(0).rolesOf(9L));
        assertEquals("峰会A", first.get(0).name());

        mockMeetingRows(2L, "沙龙B", "甲", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 5),
                new Object[][] {{9L, "EXECUTOR"}});
        List<ProjectCalendarService.Meeting> second = service.myMeetings(user(9),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertEquals(List.of("EXECUTOR"), second.get(0).rolesOf(9L));
    }

    @Test
    void queryArgsAreFlatNotNestedArray() {
        // 2026-11-26 根因回归：varargs 混传标量+toArray 会嵌套 Object[]，
        // MySQL 驱动将其序列化为 blob 绑定 IN 占位符导致日历恒空；断言参数平铺。
        mockMeetingRows(1L, "峰会A", "甲", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2),
                new Object[][] {{9L, "OWNER"}});
        service.myMeetings(user(9), LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 31));
        // varargs 实参由 mockMeetingRows 的 Answer 记录（getArguments 展开 varargs）
        assertNotNull(lastQueryArgs);
        assertEquals(5, lastQueryArgs.length, "sql + handler + to + from + 1 个 userId");
        for (int i = 2; i < lastQueryArgs.length; i++) {
            assertFalse(lastQueryArgs[i] instanceof Object[], "JDBC 参数不得为嵌套数组（实际：" + lastQueryArgs[i] + "）");
        }
    }

    @Test
    void sqlFiltersActiveAndOverlapping() {
        mockMeetingRows(1L, "峰会A", "甲", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2),
                new Object[][] {{9L, "OWNER"}});
        service.myMeetings(user(9), LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 31));
        // 断言 SQL 条件：status=ACTIVE + 区间重叠（from/to 参数顺序：meeting_start<=to、meeting_end>=from）
        String sql = String.format(ProjectCalendarService.SQL_MEETINGS, "?");
        verify(db).query(contains("p.status = 'ACTIVE'"), any(RowCallbackHandler.class), any(Object[].class));
        verify(db).query(contains("p.meeting_start_date <= ?"), any(RowCallbackHandler.class), any(Object[].class));
        verify(db).query(contains("p.meeting_end_date >= ?"), any(RowCallbackHandler.class), any(Object[].class));
    }

    // ---------- 工作台卡片 ----------

    @Test
    void deptMeetingsSectionReturnsNullForNonManager() {
        mockDeptTree(false, false);
        assertNull(service.deptMeetingsSection(user(99)));
    }

    @Test
    void myMeetingsSectionSerializesWhitelist() {
        mockMeetingRows(1L, "峰会A", "甲", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2),
                new Object[][] {{9L, "OWNER"}});
        Map<String, Object> section = service.myMeetingsSection(user(9));
        assertEquals("success", section.get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<String, Object>) section.get("data")).get("items");
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        // 白名单断言：只允许这些键（需求验收：响应不含预算/客户/专家等字段）
        assertEquals(Set.of("projectId", "name", "ownerName", "meetingStartDate", "meetingEndDate", "roles", "target"),
                item.keySet());
    }
}
