package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.service.ProfileOverviewService.DepartmentForm;
import com.jshz.expert.service.ProfileOverviewService.Member;
import com.jshz.expert.service.ProfileOverviewService.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织树规则：总经理唯一与顶替、总经办负责人固定、部门负责人、成员可见范围、停用自动撤销。
 * 数据库以记录式替身模拟，只验证业务规则与落库语句；真实 SQL 由隔离 MySQL 另行验证。
 */
class ProfileOrganizationRulesTest {

    /** id、父级、名称、seed_key、负责人 */
    private record Dept(long id, Long parent, String name, String seed, Long leader) {}

    private static final long ROOT = 1L, OFFICE = 2L, PROJECT = 3L, PROJECT_1 = 4L, GENERAL = 6L, ADMIN_AFFAIRS = 5L;
    private static final long GM_ROLE = 54L;
    /** 甲=项目部负责人，乙=普通成员，壬=现任总经理 */
    private static final long JIA = 5L, YI = 8L, REN = 9L;

    private final List<Invocation> calls = new ArrayList<>();
    private final List<Dept> depts = new ArrayList<>(List.of(
            new Dept(ROOT, null, "总经理", "GENERAL_MANAGER", REN),
            new Dept(OFFICE, ROOT, "总经办", "GENERAL_OFFICE", null),
            new Dept(PROJECT, OFFICE, "项目部", "PROJECT", JIA),
            new Dept(PROJECT_1, PROJECT, "项目一部", "PROJECT_1", null),
            new Dept(GENERAL, OFFICE, "综合部", "HR_ADMIN", null),
            new Dept(ADMIN_AFFAIRS, GENERAL, "行政", "ADMIN_AFFAIRS", null)));
    private final Map<Long, String> activeUsers = Map.of(JIA, "甲", YI, "乙", REN, "壬");

    private ResultSet membershipRow;
    private long memberCount = 0L;
    @SuppressWarnings("unchecked")
    private ResultSet[] memberRows;
    private JdbcTemplate jdbc;
    private PermissionService permissions;
    private UserAuthStateService authState;
    private OperationLogService logs;
    private ProfileOverviewService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class, this::answer);
        permissions = mock(PermissionService.class);
        authState = mock(UserAuthStateService.class);
        logs = mock(OperationLogService.class);
        service = new ProfileOverviewService(jdbc, permissions, authState, logs);
    }

    @Test
    void appointingGeneralManagerReplacesPreviousAndClearsDepartment() throws Exception {
        String note = service.saveDepartment(ROOT, new DepartmentForm(null, "总经理", true, YI));

        assertThat(note).isEqualTo("任命总经理「乙」");
        assertUpdate("DELETE FROM profile_membership WHERE user_id=?", YI);
        assertUpdate("INSERT IGNORE INTO user_role (user_id,role_id) VALUES (?,?)", YI, GM_ROLE);
        assertUpdate("DELETE FROM user_role WHERE user_id=? AND role_id=?", REN, GM_ROLE);
        assertUpdate("UPDATE profile_department SET name=?,parent_id=?,active=?,leader_user_id=? WHERE id=?",
                "总经理", null, true, YI, ROOT);
        verify(authState).bumpAndEvict(YI);
        verify(authState).bumpAndEvict(REN);
        verify(permissions, org.mockito.Mockito.atLeastOnce()).clearCaches();
    }

    @Test
    void keepingTheSameGeneralManagerTouchesNoRole() {
        String note = service.saveDepartment(ROOT, new DepartmentForm(null, "总经理", true, REN));

        assertThat(note).isNull();
        assertThat(calls).noneMatch(call -> sql(call).contains("user_role"));
        verify(authState, never()).bumpAndEvict(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void dismissingGeneralManagerRemovesRole() {
        String note = service.saveDepartment(ROOT, new DepartmentForm(null, "总经理", true, null));

        assertThat(note).isEqualTo("免去总经理「壬」");
        assertUpdate("DELETE FROM user_role WHERE user_id=? AND role_id=?", REN, GM_ROLE);
        assertThat(calls).noneMatch(call -> sql(call).startsWith("INSERT IGNORE INTO user_role"));
    }

    @Test
    void generalManagerCannotBeMovedUnderAnotherDepartment() {
        assertThatThrownBy(() -> service.saveDepartment(ROOT, new DepartmentForm(OFFICE, "总经理", true, REN)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("组织树顶层");
    }

    @Test
    void generalOfficeLeaderIsAlwaysTheGeneralManager() {
        assertThatThrownBy(() -> service.saveDepartment(OFFICE, new DepartmentForm(ROOT, "总经办", true, YI)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("总经办");
    }

    @Test
    void treeKeepsASingleTopLevelNode() {
        assertThatThrownBy(() -> service.saveDepartment(null, new DepartmentForm(null, "新部门", true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("上级部门");
    }

    @Test
    void departmentLeaderMustBeAnActiveAccount() {
        assertThatThrownBy(() -> service.saveDepartment(PROJECT_1, new DepartmentForm(PROJECT, "项目一部", true, 77L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("启用的账号");
        assertThat(calls).noneMatch(call -> sql(call).startsWith("UPDATE profile_department"));
    }

    @Test
    void departmentLeaderIsRecordedForAudit() {
        String note = service.saveDepartment(PROJECT_1, new DepartmentForm(PROJECT, "项目一部", true, YI));

        assertThat(note).isEqualTo("任命「项目一部」负责人「乙」");
        assertUpdate("UPDATE profile_department SET name=?,parent_id=?,active=?,leader_user_id=? WHERE id=?",
                "项目一部", PROJECT, true, YI, PROJECT_1);
        // 无主部门的负责人自动归属本部门（替身库对 COUNT(*) 返回 0，必触发）
        assertUpdate("INSERT INTO profile_membership (user_id,department_id,position) VALUES (?,?,?)",
                YI, PROJECT_1, null);
        assertThat(calls).noneMatch(call -> sql(call).contains("user_role"));
    }

    @Test
    void disablingAccountRevokesGeneralManagerAndLeadership() {
        service.revokeLeadership(REN, "账号停用，自动撤销负责人身份");

        assertUpdate("DELETE FROM user_role WHERE user_id=? AND role_id=?", REN, GM_ROLE);
        assertUpdate("UPDATE profile_department SET leader_user_id=NULL WHERE leader_user_id=?", REN);
        verify(logs).record(null, null, "PROFILE_ORG_LEADER", "账号停用，自动撤销负责人身份：总经理",
                ProfileOverviewService.USER_ACCOUNT, REN, null);
    }

    @Test
    void accountWithoutLeadershipLeavesEverythingUntouched() {
        service.revokeLeadership(YI, "账号停用，自动撤销负责人身份");

        assertThat(calls).noneMatch(call -> sql(call).startsWith("UPDATE") || sql(call).startsWith("DELETE"));
        verify(logs, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void departmentLeaderSeesOwnDepartmentAndDescendants() {
        service.members(false, JIA, null, 1, null);

        Invocation rows = calls.stream()
                .filter(call -> sql(call).startsWith("SELECT u.id,u.username")).findFirst().orElseThrow();
        assertThat(sql(rows)).contains("m.department_id IN (?,?)");
        assertThat(tail(rows)).containsExactly(PROJECT, PROJECT_1, 20, 0L);
    }

    @Test
    void departmentLeaderCannotPeekIntoOtherDepartments() {
        assertThatThrownBy(() -> service.members(false, JIA, null, 1, GENERAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403));
        assertThat(calls).noneMatch(call -> sql(call).startsWith("SELECT COUNT(*) FROM sys_user u"));
    }

    @Test
    void generalManagerLeadingAnotherDepartmentKeepsNoMembership() {
        // 现任总经理（壬）代理其他部门负责人时不得自动获得部门归属
        String note = service.saveDepartment(PROJECT_1, new DepartmentForm(PROJECT, "项目一部", true, REN));
        assertThat(note).isEqualTo("任命「项目一部」负责人「壬」");
        assertThat(calls).noneMatch(call -> sql(call).startsWith("INSERT INTO profile_membership"));
    }

    @Test
    void plainMemberIsRefused() {
        assertThatThrownBy(() -> service.members(false, YI, null, 1, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403));
    }

    @Test
    void organizationAdminFiltersByDepartmentSubtree() {
        service.members(true, YI, null, 1, GENERAL);

        Invocation rows = calls.stream()
                .filter(call -> sql(call).startsWith("SELECT u.id,u.username")).findFirst().orElseThrow();
        assertThat(tail(rows)).containsExactly(GENERAL, ADMIN_AFFAIRS, 20, 0L);
    }

    @Test
    void generalManagerHasNoDepartmentButKeepsDelegatedLeadership() {
        depts.set(4, new Dept(GENERAL, OFFICE, "综合部", "HR_ADMIN", REN));

        Organization organization = service.organization(REN);

        assertThat(organization.generalManager()).isTrue();
        assertThat(organization.departmentPath()).isEqualTo("总经理");
        assertThat(organization.position()).isNull();
        assertThat(organization.ledDepartments())
                .containsExactly(new ProfileOverviewService.LedDepartment(GENERAL, "总经理 / 总经办 / 综合部"));
    }

    @Test
    void memberSeesFullPathAndNearestLeader() throws Exception {
        mockMembership(PROJECT_1, "职员");

        Organization organization = service.organization(YI);

        assertThat(organization.departmentPath()).isEqualTo("总经理 / 总经办 / 项目部 / 项目一部");
        assertThat(organization.position()).isEqualTo("职员");
        assertThat(organization.leaderName()).isEqualTo("甲");
        assertThat(organization.generalManager()).isFalse();
    }

    @Test
    void generalManagerIsNotAssignedADepartment() {
        assertThatThrownBy(() -> service.saveMembership(REN, new ProfileOverviewService.Membership(PROJECT_1, "职员")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("总经理不设置部门与岗位");
        assertThat(calls).noneMatch(call -> sql(call).startsWith("INSERT INTO profile_membership"));
    }

    @Test
    void memberRowsCarryLeadershipMarkers() throws Exception {
        ResultSet yi = mock(ResultSet.class);
        when(yi.getLong(1)).thenReturn(YI);
        when(yi.getString(2)).thenReturn("乙");
        when(yi.getInt(3)).thenReturn(1);
        when(yi.getObject(4, Long.class)).thenReturn(PROJECT_1);
        when(yi.getString(5)).thenReturn("职员");
        ResultSet jia = mock(ResultSet.class);
        when(jia.getLong(1)).thenReturn(JIA);
        when(jia.getString(2)).thenReturn("甲");
        when(jia.getInt(3)).thenReturn(1);
        when(jia.getObject(4, Long.class)).thenReturn(PROJECT);
        when(jia.getString(5)).thenReturn("经理");
        memberRows = new ResultSet[] {yi, jia};
        memberCount = 2L;

        List<Member> items = service.members(true, REN, null, 1, null).items();

        assertThat(items).extracting(Member::username).containsExactly("乙", "甲");
        Member leader = items.get(1);
        assertThat(leader.leaderOf()).containsExactly("总经理 / 总经办 / 项目部");
        assertThat(leader.departmentPath()).isEqualTo("总经理 / 总经办 / 项目部");
        assertThat(leader.generalManager()).isFalse();
    }

    // ---------------- 替身数据库 ----------------

    private Object answer(org.mockito.invocation.InvocationOnMock inv) throws Throwable {
        calls.add((Invocation) inv);
        Object[] args = inv.getArguments();
        String sql = (String) args[0];
        if (sql.startsWith("SELECT department_id,position")) {
            return membershipRow == null ? null : ((RowMapper<Object>) args[1]).mapRow(membershipRow, 0);
        }
        if (inv.getMethod().getName().equals("query") && sql.startsWith("SELECT id,parent_id")) {
            return depts.stream().map(dept -> mapDept((RowMapper<Object>) args[1], dept)).toList();
        }
        if (sql.startsWith("SELECT id,username FROM sys_user WHERE status=1 AND id IN")) {
            RowCallbackHandler handler = (RowCallbackHandler) args[1];
            for (Object id : List.of(args).subList(2, args.length)) {
                if (activeUsers.containsKey(id)) {
                    // RowCallbackHandler 每行回调一次，游标已定位；next() 恒为 false 以暴露回调内再次推进游标的错误
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(false);
                    when(rs.getLong(1)).thenReturn((Long) id);
                    when(rs.getString(2)).thenReturn(activeUsers.get(id));
                    handler.processRow(rs);
                }
            }
            return null;
        }
        if (sql.startsWith("SELECT username FROM sys_user WHERE id=? AND status=1")) {
            String name = activeUsers.get(args[args.length - 1]);
            if (name == null) return List.of();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(name);
            return List.of(((RowMapper<String>) args[1]).mapRow(rs, 0));
        }
        if (sql.startsWith("SELECT id FROM role WHERE code=?")) return List.of(GM_ROLE);
        if (sql.startsWith("SELECT u.id,u.username")) {
            RowMapper<Member> mapper = (RowMapper<Member>) args[1];
            List<Member> rows = new ArrayList<>();
            for (ResultSet row : memberRows == null ? new ResultSet[0] : memberRows) rows.add(mapper.mapRow(row, 0));
            return rows;
        }
        if (sql.startsWith("SELECT COUNT(*) FROM sys_user u")) return memberCount;
        if (sql.startsWith("SELECT COUNT(*) FROM sys_user WHERE id=?")) return 1L;
        if (sql.startsWith("SELECT COUNT(*) FROM profile_membership")) return 0L;
        return Answers.RETURNS_DEFAULTS.answer(inv);
    }

    private Object mapDept(RowMapper<Object> mapper, Dept dept) {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(dept.id());
            when(rs.getObject("parent_id", Long.class)).thenReturn(dept.parent());
            when(rs.getString("name")).thenReturn(dept.name());
            when(rs.getBoolean("active")).thenReturn(true);
            when(rs.getString("seed_key")).thenReturn(dept.seed());
            when(rs.getObject("leader_user_id", Long.class)).thenReturn(dept.leader());
            return mapper.mapRow(rs, 0);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void mockMembership(long departmentId, String position) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(departmentId);
        when(rs.getString(2)).thenReturn(position);
        membershipRow = rs;
    }

    private void assertUpdate(String sql, Object... args) {
        assertThat(calls).anyMatch(call -> sql.equals(sql(call))
                && java.util.Arrays.equals(args, tail(call)));
    }

    private static String sql(Invocation invocation) {
        return (String) invocation.getArgument(0);
    }

    /** 去掉 SQL 与映射器/回调之后的实参。 */
    private static Object[] tail(Invocation invocation) {
        Object[] args = invocation.getArguments();
        int first = args[1] instanceof RowMapper || args[1] instanceof RowCallbackHandler ? 2 : 1;
        return java.util.Arrays.copyOfRange(args, first, args.length);
    }
}
