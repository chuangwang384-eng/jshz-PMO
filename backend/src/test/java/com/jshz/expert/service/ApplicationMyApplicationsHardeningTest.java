package com.jshz.expert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.ApprovalConfigMapper;
import com.jshz.expert.mapper.ApprovalConfigUserMapper;
import com.jshz.expert.mapper.ApprovalRecordMapper;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「我的申请」第二轮审查回归（2026-11-25）。
 *
 * <p>覆盖：批量删除只删本人「已驳回/已撤回」单据并返回实际条数；重新提交时清空文本字段视为撤回该变更
 * （不再把空串写进变更）；临时照片失效被剔除时响应必须回传被剔除的字段；重提只校验本次改动键的敏感字段格式；
 * 合同申请的待办/撤回通知文案不再误显示为「专家信息修改申请」；退回修改单的失效照片键随列表返回。</p>
 */
class ApplicationMyApplicationsHardeningTest {

    private static final int PENDING = 0;
    private static final int APPROVED = 1;
    private static final int REJECTED = 2;
    private static final int WITHDRAWN = 3;
    private static final int RETURNED = 4;

    private ApplicationMapper applicationMapper;
    private PhotoStorageService photoStorageService;
    private NotificationService notificationService;
    private RoleMapper roleMapper;
    private UserRoleMapper userRoleMapper;
    private ExpertService expertService;
    private OperationLogService operationLogService;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(ApplicationMapper.class);
        photoStorageService = mock(PhotoStorageService.class);
        notificationService = mock(NotificationService.class);
        expertService = mock(ExpertService.class);
        operationLogService = mock(OperationLogService.class);
        ApprovalConfigMapper approvalConfigMapper = mock(ApprovalConfigMapper.class);
        roleMapper = mock(RoleMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        service = new ApplicationService(
                approvalConfigMapper,
                mock(ApprovalConfigUserMapper.class),
                mock(ApprovalRecordMapper.class),
                expertService,
                photoStorageService,
                roleMapper,
                new ObjectMapper(),
                notificationService,
                mock(SysUserMapper.class),
                userRoleMapper,
                mock(PermissionService.class),
                mock(ProjectContractMapper.class),
                mock(ProjectExpertMapper.class),
                operationLogService);
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                Application.class);
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(applicationMapper.delete(any())).thenReturn(1);
        ApprovalConfig config = new ApprovalConfig();
        config.setFlowKey(ApplicationService.FLOW_KEY);
        config.setFlowName("专家信息修改审批");
        config.setEnabled(1);
        config.setApproverRole("APPROVER");
        config.setId(1L);
        when(approvalConfigMapper.selectOne(any())).thenReturn(config);
    }

    // ---------------- 批量删除 ----------------

    /** 混合批次：只删本人且已驳回/已撤回的条目，其余跳过，不中断整批 */
    @Test
    void batchDeleteRemovesOnlyOwnTerminalApplications() {
        appOf(1L, WITHDRAWN, "alice", 5L);
        appOf(2L, REJECTED, "alice", 5L);
        appOf(3L, PENDING, "alice", 5L);      // 待审批：不可删
        appOf(4L, APPROVED, "alice", 5L);     // 已通过：不可删
        appOf(5L, RETURNED, "alice", 5L);     // 退回修改：不可删
        appOf(6L, REJECTED, "carol", 9L);     // 他人单据：不可删
        // id=7 不存在

        int removed = service.deleteApplications(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 1L), userOf(5L, "alice"));

        assertEquals(2, removed);
        verify(applicationMapper, times(2)).delete(any());
        verify(operationLogService, times(2)).record(any(), eq("alice"),
                eq(OperationLogService.ACTION_DELETE_APPLICATION), anyString(), anyString(), anyLong(), any());
    }

    /** 重复 id 只处理一次；非本人整批不删 */
    @Test
    void batchDeleteIsIdempotentOnDuplicatesAndRejectsOthersSilently() {
        appOf(1L, WITHDRAWN, "carol", 9L);

        assertEquals(0, service.deleteApplications(List.of(1L, 1L), userOf(5L, "alice")));
        verify(applicationMapper, never()).delete(any());
    }

    // ---------------- 重新提交：清空 = 撤回变更 ----------------

    /** 修改申请带旧值快照：把「科室」清空应从变更中移除该键，而不是写入空串 */
    @Test
    void resubmitBlankTextFieldWithdrawsThatChange() {
        Application app = appOf(100L, RETURNED, "alice", 5L);
        app.setBizData("{\"expertId\":9,\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"oldData\":{\"姓名\":\"张三\",\"科室\":\"内科\"},"
                + "\"changes\":{\"姓名\":\"张三\",\"科室\":\"外科\"}}");

        service.resubmit(100L, Map.of("姓名", "张三", "科室", "  "), "r", userOf(5L, "alice"));

        assertFalse(app.getBizData().contains("外科"), "清空的字段不应保留在变更中: " + app.getBizData());
        assertTrue(app.getBizData().contains("\"oldData\""), "旧值快照必须保留");
        // 姓名未变化：仍保留在变更中（值与原申请一致，不属清空）
        assertTrue(app.getBizData().contains("张三"));
    }

    /** 必填字段清空仍被拒绝（不得借「撤回变更」绕过姓名必填） */
    @Test
    void resubmitBlankRequiredFieldStillRejected() {
        appOf(100L, RETURNED, "alice", 5L).setBizData("{\"expertId\":9,\"reason\":\"r\","
                + "\"oldData\":{\"姓名\":\"张三\"},\"changes\":{\"姓名\":\"张三\"}}");

        com.jshz.expert.common.BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.jshz.expert.common.BusinessException.class,
                () -> service.resubmit(100L, Map.of("姓名", ""), "r", userOf(5L, "alice")));
        assertTrue(String.valueOf(ex.getMessage()).contains("姓名"), ex.getMessage());
    }

    /** 新增申请无旧值快照：清空维持原语义（非必填字段允许为空） */
    @Test
    void resubmitBlankOnCreateRequestKeepsPreviousSemantics() {
        Application app = appOf(100L, RETURNED, "alice", 5L);
        app.setApplicationType("EXPERT_CREATE");
        app.setBizData("{\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"changes\":{\"姓名\":\"张三\",\"单位\":\"某医院\",\"科室\":\"内科\"}}");

        assertDoesNotThrow(() -> service.resubmit(100L, Map.of("姓名", "张三", "科室", ""), "r", userOf(5L, "alice")));
        assertTrue(app.getBizData().contains("\"科室\":\"\""), app.getBizData());
    }

    // ---------------- 重新提交：失效照片回传 ----------------

    /** 临时文件丢失的照片键被剔除时，响应必须带 droppedPhotoFields，不能静默成功 */
    @Test
    void resubmitReportsDroppedPhotoFields() {
        Application app = appOf(100L, RETURNED, "alice", 5L);
        app.setBizData("{\"expertId\":9,\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"changes\":{\"姓名\":\"张三\",\"照片\":\"TMP:gone.png\",\"身份证正面\":\"TMP:kept.png\"}}");
        when(photoStorageService.existsTempPhoto("gone.png")).thenReturn(false);
        when(photoStorageService.existsTempPhoto("kept.png")).thenReturn(true);

        Application result = service.resubmit(100L, Map.of("姓名", "张三"), "r", userOf(5L, "alice"));

        assertEquals(PENDING, result.getStatus());
        assertEquals(List.of("照片"), result.getDroppedPhotoFields());
        assertFalse(app.getBizData().contains("TMP:gone.png"));
        assertTrue(app.getBizData().contains("TMP:kept.png"));
    }

    /** 没有照片失效时不带提示字段 */
    @Test
    void resubmitWithoutPhotoLossReportsNothingDropped() {
        appOf(100L, RETURNED, "alice", 5L).setBizData(
                "{\"expertId\":9,\"expertName\":\"张三\",\"reason\":\"r\",\"changes\":{\"姓名\":\"张三\"}}");

        Application result = service.resubmit(100L, Map.of("姓名", "张三"), "r", userOf(5L, "alice"));

        assertTrue(result.getDroppedPhotoFields().isEmpty());
    }

    // ---------------- 重提：敏感字段只校验本次改动键 ----------------

    /** 历史单里的存量联系方式是座机号（不符合现行格式）：未改动它时不应阻塞重提 */
    @Test
    void resubmitIgnoresUntouchedLegacySensitiveValue() {
        appOf(100L, RETURNED, "alice", 5L).setBizData("{\"expertId\":9,\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"changes\":{\"姓名\":\"张三丰\",\"联系方式\":\"021-12345678\"}}");

        assertDoesNotThrow(() -> service.resubmit(100L, Map.of("姓名", "张三丰"), "r", userOf(5L, "alice")));
    }

    /** 本次改动的联系方式仍须符合格式 */
    @Test
    void resubmitStillValidatesTouchedSensitiveValue() {
        appOf(100L, RETURNED, "alice", 5L).setBizData("{\"expertId\":9,\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"changes\":{\"姓名\":\"张三\",\"联系方式\":\"13800000000\"}}");

        com.jshz.expert.common.BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.jshz.expert.common.BusinessException.class,
                () -> service.resubmit(100L, Map.of("联系方式", "not-a-phone"), "r", userOf(5L, "alice")));
        assertTrue(String.valueOf(ex.getMessage()).contains("联系方式"), ex.getMessage());
    }

    // ---------------- 通知文案 ----------------

    /** 合同申请重提后的待办通知文案应为「专家合同审批申请」 */
    @Test
    void contractResubmitNotifiesWithContractTypeText() {
        Application app = appOf(100L, RETURNED, "alice", 5L);
        app.setApplicationType("CONTRACT");
        app.setFlowKey(ApplicationService.FLOW_KEY_CONTRACT);
        app.setBizData("{\"contractId\":3,\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"changes\":{\"身份证号\":\"110101199001011234\"}}");

        // 审批人快照为空时回退到 approver_role 下的用户：APPROVER 角色下仅用户 7
        Role role = new Role();
        role.setId(2L);
        role.setCode("APPROVER");
        when(roleMapper.selectOne(any())).thenReturn(role);
        UserRole binding = new UserRole();
        binding.setRoleId(2L);
        binding.setUserId(7L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(binding));

        service.resubmit(100L, Map.of(), "r", userOf(5L, "alice"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq(7L), eq(NotificationService.TYPE_APPROVAL_TODO),
                title.capture(), anyString(), eq("/profile/approval/manage"));
        assertTrue(title.getValue().contains("专家合同审批申请"), title.getValue());
        assertFalse(title.getValue().contains("专家信息修改"), title.getValue());
    }

    // ---------------- 列表：退回单失效照片键 ----------------

    /** 退回修改单的临时照片已丢失：fillView 应标出失效字段 */
    @Test
    void fillViewMarksExpiredPhotosOnReturnedApplication() {
        Application app = appOf(100L, RETURNED, "alice", 5L);
        app.setBizData("{\"expertId\":9,\"expertName\":\"张三\",\"reason\":\"r\","
                + "\"changes\":{\"姓名\":\"张三\",\"照片\":\"TMP:gone.png\"}}");
        when(photoStorageService.existsTempPhoto("gone.png")).thenReturn(false);

        Application viewed = ReflectionTestUtils.invokeMethod(service, "fillView", app, List.of());

        assertEquals(List.of("照片"), viewed.getExpiredPhotoFields());
    }

    /** 待审批单不探测文件系统：即使文件不存在也不标记 */
    @Test
    void fillViewDoesNotProbePhotosForPendingApplication() {
        Application app = appOf(100L, PENDING, "alice", 5L);
        app.setBizData("{\"expertId\":9,\"changes\":{\"照片\":\"TMP:gone.png\"}}");

        Application viewed = ReflectionTestUtils.invokeMethod(service, "fillView", app, List.of());

        assertNull(viewed.getExpiredPhotoFields());
        verify(photoStorageService, never()).existsTempPhoto(anyString());
    }

    // ---------------- helpers ----------------

    private Application appOf(Long id, int status, String applicant, Long applicantId) {
        Application app = new Application();
        app.setId(id);
        app.setApplicant(applicant);
        app.setApplicantId(applicantId);
        app.setApplicationType("EXPERT_MODIFY");
        app.setFlowKey(ApplicationService.FLOW_KEY);
        app.setStatus(status);
        app.setBizData("{\"expertId\":9,\"changes\":{}}");
        when(applicationMapper.selectById(id)).thenReturn(app);
        return app;
    }

    private AuthUser userOf(Long id, String username) {
        return new AuthUser(id, username, false, false, Set.of(), Set.of());
    }
}
