package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.Application;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「我的申请」审查修复回归测试（2026-11-24，Q1/Q4/Q9/删除功能）。
 *
 * <p>覆盖：biz_data 损坏或变更缺失时审批必须阻断（不再「空转置为已通过」）；
 * 重新提交对已失效的临时照片降级剔除而不是硬拒；删除仅允许本人对「已驳回/已撤回」单据执行；
 * 姓名等文本字段的白名单校验；分页参数钳制。</p>
 */
class ApplicationApprovalGuardTest {

    private static final int PENDING = 0;
    private static final int APPROVED = 1;
    private static final int REJECTED = 2;
    private static final int WITHDRAWN = 3;
    private static final int RETURNED = 4;

    private ApplicationMapper applicationMapper;
    private ApprovalRecordMapper approvalRecordMapper;
    private ApprovalConfigMapper approvalConfigMapper;
    private PhotoStorageService photoStorageService;
    private ExpertService expertService;
    private OperationLogService operationLogService;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(ApplicationMapper.class);
        approvalRecordMapper = mock(ApprovalRecordMapper.class);
        approvalConfigMapper = mock(ApprovalConfigMapper.class);
        photoStorageService = mock(PhotoStorageService.class);
        expertService = mock(ExpertService.class);
        operationLogService = mock(OperationLogService.class);
        service = new ApplicationService(
                approvalConfigMapper,
                mock(ApprovalConfigUserMapper.class),
                approvalRecordMapper,
                expertService,
                photoStorageService,
                mock(RoleMapper.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(NotificationService.class),
                mock(SysUserMapper.class),
                mock(UserRoleMapper.class),
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
    }

    // ---------------- Q1：biz_data 损坏不得「空审批通过」 ----------------

    /** biz_data 非法 JSON：解析失败必须阻断审批，且不得触碰专家档案 */
    @Test
    void approveModifyBlocksCorruptedBizData() {
        Application app = appOf(PENDING, "{not-a-json", "EXPERT_MODIFY");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approveModify(app, "同意", userOf(1L, "approver")));

        assertTrue(String.valueOf(ex.getMessage()).contains("损坏"), "应提示申请内容损坏: " + ex.getMessage());
        verify(expertService, never()).update(any(), any(), any());
        assertEquals(PENDING, app.getStatus(), "阻断后不得把单据置为已通过");
    }

    /** biz_data 合法但缺少 changes：同样阻断（否则审批通过但字段一个字都没更新） */
    @Test
    void approveModifyBlocksMissingChanges() {
        Application app = appOf(PENDING, "{\"expertId\":9,\"expertName\":\"张三\"}", "EXPERT_MODIFY");

        assertThrows(BusinessException.class, () -> service.approveModify(app, null, userOf(1L, "approver")));
        verify(expertService, never()).update(any(), any(), any());
    }

    // ---------------- Q4：文本字段校验 ----------------

    /** 重新提交把姓名改成空白：必须拒绝（新增侧一直有此校验，此前修改/重提漏掉） */
    @Test
    void resubmitRejectsBlankName() {
        Application app = appOf(RETURNED,
                "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{\"姓名\":\"张三\"},\"reason\":\"r\"}", "EXPERT_MODIFY");
        when(photoStorageService.existsTempPhoto(anyString())).thenReturn(true);
        mockEnabledConfig();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resubmit(100L, Map.of("姓名", "  "), null, userOf(5L, "alice")));
        assertTrue(String.valueOf(ex.getMessage()).contains("姓名不能为空"), ex.getMessage());
    }

    // ---------------- Q9：临时照片失效降级 ----------------

    /** 原照片临时文件已丢失且本次未重传：剔除该键后继续提交，而不是硬拒导致单据永久滞留 */
    @Test
    void resubmitDropsMissingTempPhotoInsteadOfFailing() {
        Application app = appOf(RETURNED,
                "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{\"姓名\":\"张三\",\"照片\":\"TMP:gone.png\"},\"reason\":\"r\"}",
                "EXPERT_MODIFY");
        when(photoStorageService.existsTempPhoto("gone.png")).thenReturn(false);
        mockEnabledConfig();

        Application result = assertDoesNotThrow(
                () -> service.resubmit(100L, Map.of("姓名", "张三丰"), "补充材料", userOf(5L, "alice")));

        assertEquals(PENDING, result.getStatus());
        assertFalse(String.valueOf(result.getBizData()).contains("TMP:gone.png"), "失效照片键应被剔除");
        assertTrue(String.valueOf(result.getBizData()).contains("张三丰"));
    }

    // ---------------- 删除申请 ----------------

    /** 已撤回可删：同事务删进度记录 + 写操作日志留痕 */
    @Test
    void deleteAllowsWithdrawnForOwner() {
        Application app = appOf(WITHDRAWN, "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{}}", "EXPERT_MODIFY");

        assertDoesNotThrow(() -> service.deleteApplication(100L, userOf(5L, "alice")));

        verify(operationLogService).record(any(), eq("alice"),
                eq(OperationLogService.ACTION_DELETE_APPLICATION), anyString(), eq("APPLICATION"), eq(100L), any());
    }

    @Test
    void deleteAllowsRejectedForOwner() {
        Application app = appOf(REJECTED, "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{}}", "EXPERT_MODIFY");
        assertDoesNotThrow(() -> service.deleteApplication(100L, userOf(5L, "alice")));
    }

    /** 待审批 / 已通过不可删：待审批须先撤回，已通过涉及专家档案变更留痕 */
    @Test
    void deleteRejectsNonTerminalOrApproved() {
        appOf(PENDING, "{\"expertId\":9,\"changes\":{}}", "EXPERT_MODIFY");
        assertThrows(BusinessException.class, () -> service.deleteApplication(100L, userOf(5L, "alice")));

        appOf(APPROVED, "{\"expertId\":9,\"changes\":{}}", "EXPERT_MODIFY");
        assertThrows(BusinessException.class, () -> service.deleteApplication(100L, userOf(5L, "alice")));
    }

    @Test
    void deleteRejectsNonApplicant() {
        appOf(WITHDRAWN, "{\"expertId\":9,\"changes\":{}}", "EXPERT_MODIFY");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteApplication(100L, userOf(9L, "carol")));
        assertTrue(String.valueOf(ex.getMessage()).contains("仅申请人本人"), ex.getMessage());
        verify(applicationMapper, never()).delete(any());
    }

    // ---------------- Q2：分页参数钳制 ----------------

    @Test
    void clampKeepsPageAndSizeWithinBounds() {
        assertEquals(1, ParamUtils.clamp(-1, 1, Long.MAX_VALUE));
        assertEquals(1, ParamUtils.clamp(0, 1, Long.MAX_VALUE));
        assertEquals(1, ParamUtils.clamp(1, 1, ApplicationService.MAX_PAGE_SIZE));
        assertEquals(100, ParamUtils.clamp(1000, 1, ApplicationService.MAX_PAGE_SIZE));
        assertEquals(100, ApplicationService.MAX_PAGE_SIZE);
    }

    // ---------------- helpers ----------------

    private Application appOf(int status, String bizData, String type) {
        Application app = new Application();
        app.setId(100L);
        app.setApplicant("alice");
        app.setApplicantId(5L);
        app.setApplicationType(type);
        app.setFlowKey("EXPERT");
        app.setStatus(status);
        app.setBizData(bizData);
        when(applicationMapper.selectById(100L)).thenReturn(app);
        return app;
    }

    private void mockEnabledConfig() {
        ApprovalConfig config = new ApprovalConfig();
        config.setId(1L);
        config.setFlowKey("EXPERT");
        config.setFlowName("专家信息修改审批");
        config.setEnabled(1);
        config.setApproverRole("APPROVER");
        when(approvalConfigMapper.selectOne(any())).thenReturn(config);
    }

    private AuthUser userOf(Long id, String name) {
        return new AuthUser(id, name, false, false, Set.of(), Set.of());
    }
}
