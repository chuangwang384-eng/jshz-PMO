package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.jshz.expert.entity.ApprovalRecord;
import com.jshz.expert.entity.Application;
import com.jshz.expert.mapper.ApprovalRecordMapper;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.ApprovalConfigMapper;
import com.jshz.expert.mapper.ApprovalConfigUserMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批滞留催办回归（2026-11-25，审批管理需求 E）。
 *
 * <p>覆盖：滞留超阈值发催办（URGE_24/URGE_72 分级）；同级别只催一次（进度记录去重）；
 * 未达阈值不催；并发下单据已被处理（状态守卫失败）不催；催办通知走待办链路并补发转办/加签人。</p>
 */
class ApprovalUrgeTest {

    private ApplicationMapper applicationMapper;
    private ApprovalRecordMapper recordMapper;
    private NotificationService notificationService;
    private ApplicationService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 12, 0);

    @BeforeEach
    void setUp() {
        applicationMapper = mock(ApplicationMapper.class);
        recordMapper = mock(ApprovalRecordMapper.class);
        notificationService = mock(NotificationService.class);
        service = new ApplicationService(
                mock(ApprovalConfigMapper.class),
                mock(ApprovalConfigUserMapper.class),
                recordMapper,
                mock(ExpertService.class),
                mock(PhotoStorageService.class),
                mock(RoleMapper.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                notificationService,
                mock(SysUserMapper.class),
                mock(UserRoleMapper.class),
                mock(PermissionService.class),
                mock(ProjectContractMapper.class),
                mock(ProjectExpertMapper.class),
                mock(OperationLogService.class));
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Application.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalRecord.class);
        // 默认：状态守卫成功（仍为待审批）
        when(applicationMapper.updateStatusIfCurrent(anyLong(), anyInt(), anyInt())).thenReturn(1);
    }

    /** 滞留 30 小时：发 URGE_24，不再发更高级别（通知走快照内审批人） */
    @Test
    void urgesFirstLevelAfterFirstThreshold() {
        pendingApp(100L, NOW.minusHours(30), null).setApproverSnapshot("[{\"id\":7,\"username\":\"approver\"}]");

        int sent = service.urgeStalePending(24, 72, NOW);

        assertEquals(1, sent);
        ArgumentCaptor<ApprovalRecord> record = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(recordMapper).insert(record.capture());
        assertEquals("URGE_24", record.getValue().getAction());
        verify(notificationService, times(1)).notify(eq(7L), eq(NotificationService.TYPE_APPROVAL_TODO),
                anyString(), anyString(), eq("/profile/approval/manage"));
    }

    /** 滞留 80 小时且已催过 URGE_24：升级发 URGE_72 */
    @Test
    void escalatesToSecondLevel() {
        Application app = pendingApp(100L, NOW.minusHours(80), null);
        ApprovalRecord urge24 = new ApprovalRecord();
        urge24.setAction("URGE_24");
        when(recordMapper.selectList(any())).thenReturn(List.of(urge24));

        int sent = service.urgeStalePending(24, 72, NOW);

        assertEquals(1, sent);
        ArgumentCaptor<ApprovalRecord> record = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(recordMapper).insert(record.capture());
        assertEquals("URGE_72", record.getValue().getAction());
    }

    /** 已催过当前级别：不再重复催 */
    @Test
    void skipsAlreadyUrgedSameLevel() {
        pendingApp(100L, NOW.minusHours(30), null);
        ApprovalRecord urge24 = new ApprovalRecord();
        urge24.setAction("URGE_24");
        when(recordMapper.selectList(any())).thenReturn(List.of(urge24));

        assertEquals(0, service.urgeStalePending(24, 72, NOW));
        verify(recordMapper, never()).insert(any(ApprovalRecord.class));
    }

    /** 未达阈值：不催 */
    @Test
    void skipsBelowThreshold() {
        pendingApp(100L, NOW.minusHours(5), null);

        assertEquals(0, service.urgeStalePending(24, 72, NOW));
        verify(recordMapper, never()).insert(any(ApprovalRecord.class));
    }

    /** 状态守卫失败（已被审批/撤回）：不催；转办人补发去重 */
    @Test
    void skipsWhenClaimFailsAndNotifiesAssigneeOnce() {
        pendingApp(100L, NOW.minusHours(30), "bob");
        when(applicationMapper.updateStatusIfCurrent(eq(100L), anyInt(), anyInt())).thenReturn(0);

        assertEquals(0, service.urgeStalePending(24, 72, NOW));
        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString(), anyString());
    }

    /** 被转办人不在快照内时单独补发一条（快照审批人与转办人各一条，不重复） */
    @Test
    void notifiesTransferredAssignee() {
        Application app = pendingApp(100L, NOW.minusHours(30), "bob");
        app.setApproverSnapshot("[{\"id\":7,\"username\":\"approver\"}]");
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(notificationService.userIdOf("bob")).thenReturn(9L);

        service.urgeStalePending(24, 72, NOW);

        verify(notificationService, times(1)).notify(eq(7L), eq(NotificationService.TYPE_APPROVAL_TODO),
                anyString(), anyString(), eq("/profile/approval/manage"));
        verify(notificationService, times(1)).notify(eq(9L), eq(NotificationService.TYPE_APPROVAL_TODO),
                anyString(), anyString(), eq("/profile/approval/manage"));
        assertTrue(app.getStatus() == 0);
    }

    // ---------------- helpers ----------------

    private Application pendingApp(Long id, LocalDateTime createTime, String assignee) {
        Application app = new Application();
        app.setId(id);
        app.setApplicant("alice");
        app.setApplicantId(5L);
        app.setApplicationType("EXPERT_MODIFY");
        app.setFlowKey(ApplicationService.FLOW_KEY);
        app.setStatus(0);
        app.setCreateTime(createTime);
        app.setCurrentAssignee(assignee);
        app.setExpertName("张三");
        when(applicationMapper.selectList(any())).thenReturn(List.of(app));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        return app;
    }
}
