package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Application;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.ApprovalConfigMapper;
import com.jshz.expert.mapper.ApprovalConfigUserMapper;
import com.jshz.expert.mapper.ApprovalRecordMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H2 回归测试：审批并发抢占（check-then-act → 条件更新 + 影响行数判定）。
 *
 * <p>核心断言：{@code claimPending} 以 {@code status=0} 为条件更新，影响行数为 0 时抛 409 且不改内存状态；
 * 影响行数为 1 时才推进状态。另对 Mapper 的 SQL 注解做结构校验，防止 WHERE 条件被误删。</p>
 */
class ApplicationConcurrencyGuardTest {

    private static final int PENDING = 0;
    private static final int APPROVED = 1;
    private static final int REJECTED = 2;

    private ApplicationMapper applicationMapper;
    private ApplicationService service;
    private Method claimPending;

    @BeforeEach
    void setUp() throws Exception {
        applicationMapper = mock(ApplicationMapper.class);
        service = new ApplicationService(
                mock(ApprovalConfigMapper.class),
                mock(ApprovalConfigUserMapper.class),
                mock(ApprovalRecordMapper.class),
                mock(ExpertService.class),
                mock(PhotoStorageService.class),
                mock(RoleMapper.class),
                mock(com.fasterxml.jackson.databind.ObjectMapper.class),
                mock(NotificationService.class),
                mock(SysUserMapper.class),
                mock(UserRoleMapper.class),
                mock(PermissionService.class),
                mock(ProjectContractMapper.class),
                mock(ProjectExpertMapper.class),
                mock(OperationLogService.class));
        // ServiceImpl.baseMapper 由 Spring 注入，单测手工注入
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        claimPending = ApplicationService.class.getDeclaredMethod("claimPending", Application.class, int.class);
        claimPending.setAccessible(true);
    }

    /** 并发场景：后到的审批人抢占失败，必须抛 409 且不得就地修改状态 */
    @Test
    void secondApproverFailsWhenNoRowAffected() {
        Application app = pendingApplication(77L);
        when(applicationMapper.updateStatusIfCurrent(77L, PENDING, APPROVED)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> invokeClaim(app, APPROVED));

        assertEquals(409, ex.getCode(), "并发冲突应返回 409 业务码");
        assertEquals(PENDING, app.getStatus(), "抢占失败不得把内存状态改成已通过");
    }

    /** 首家审批人抢占成功：影响行数 1，状态推进 */
    @Test
    void firstApproverSucceedsWhenExactlyOneRowAffected() {
        Application app = pendingApplication(88L);
        when(applicationMapper.updateStatusIfCurrent(88L, PENDING, APPROVED)).thenReturn(1);

        invokeClaim(app, APPROVED);

        verify(applicationMapper).updateStatusIfCurrent(88L, PENDING, APPROVED);
        assertEquals(APPROVED, app.getStatus());
    }

    /** 驳回同样走条件更新，预期状态参数必须是「待审批」而非目标状态，否则条件恒真退化为无条件更新 */
    @Test
    void rejectClaimsFromPendingToo() {
        Application app = pendingApplication(99L);
        when(applicationMapper.updateStatusIfCurrent(99L, PENDING, REJECTED)).thenReturn(1);

        invokeClaim(app, REJECTED);

        verify(applicationMapper).updateStatusIfCurrent(99L, PENDING, REJECTED);
        assertEquals(REJECTED, app.getStatus());
    }

    /** 结构校验：Mapper 的 UPDATE 必须带 status 条件，防止被改成无条件更新后静默失效 */
    @Test
    void mapperSqlKeepsStatusPredicate() throws Exception {
        Update annotation = ApplicationMapper.class
                .getMethod("updateStatusIfCurrent", Long.class, int.class, int.class)
                .getAnnotation(Update.class);

        String sql = String.join(" ", annotation.value()).replaceAll("\\s+", " ");
        assertTrue(sql.startsWith("UPDATE application SET status = #{targetStatus}"), "应为按 id 的状态更新: " + sql);
        assertTrue(sql.contains("WHERE id = #{id} AND status = #{expectedStatus}"),
                "缺少 status 条件即退化为无条件更新，并发保护失效: " + sql);
    }

    private Application pendingApplication(Long id) {
        Application app = new Application();
        app.setId(id);
        app.setStatus(PENDING);
        return app;
    }

    private void invokeClaim(Application app, int targetStatus) {
        try {
            claimPending.invoke(service, app, targetStatus);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
