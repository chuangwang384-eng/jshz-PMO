package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重新提交（resubmit）回归测试：2026-11-24 放开退回修改后的照片重传。
 *
 * <p>覆盖：照片 patch 合法（TMP: 临时文件存在时接受并入 biz_data.changes）、
 * 被替换旧临时照片在提交后清理、CONTRACT 申请仍拒绝照片字段、
 * 申请人身份按 applicant_id/applicant 双条件判定（用户改名后仍可操作，非本人拒绝）。</p>
 */
class ApplicationResubmitTest {

    private static final int RETURNED = 4;

    private ApplicationMapper applicationMapper;
    private ApprovalConfigMapper approvalConfigMapper;
    private PhotoStorageService photoStorageService;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(ApplicationMapper.class);
        approvalConfigMapper = mock(ApprovalConfigMapper.class);
        photoStorageService = mock(PhotoStorageService.class);
        service = new ApplicationService(
                approvalConfigMapper,
                mock(ApprovalConfigUserMapper.class),
                mock(ApprovalRecordMapper.class),
                mock(ExpertService.class),
                photoStorageService,
                mock(RoleMapper.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(NotificationService.class),
                mock(SysUserMapper.class),
                mock(UserRoleMapper.class),
                mock(PermissionService.class),
                mock(ProjectContractMapper.class),
                mock(ProjectExpertMapper.class),
                mock(OperationLogService.class));
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        // resubmit 用 lambdaUpdate().set(...)：纯 Mock 环境需手工初始化实体列缓存，否则报 can not find lambda cache
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                Application.class);
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(photoStorageService.existsTempPhoto(anyString())).thenReturn(true);

        ApprovalConfig config = new ApprovalConfig();
        config.setId(1L);
        config.setFlowKey("EXPERT");
        config.setFlowName("专家信息修改审批");
        config.setEnabled(1);
        config.setApproverRole("APPROVER");
        when(approvalConfigMapper.selectOne(any())).thenReturn(config);
    }

    private Application appOf(String type, String applicant, Long applicantId, String bizData) {
        Application app = new Application();
        app.setId(100L);
        app.setApplicant(applicant);
        app.setApplicantId(applicantId);
        app.setApplicationType(type);
        app.setFlowKey("EXPERT");
        app.setStatus(RETURNED);
        app.setBizData(bizData);
        when(applicationMapper.selectById(100L)).thenReturn(app);
        return app;
    }

    private AuthUser userOf(Long id, String name) {
        return new AuthUser(id, name, false, false, Set.of(), Set.of());
    }

    /** 照片重传：TMP: 值合法即接受，被替换的旧临时照片在（无事务上下文时立即执行的）提交后清理 */
    @Test
    void resubmitAcceptsPhotoPatchAndCleansReplacedTempPhoto() {
        appOf("EXPERT_MODIFY", "alice", 5L,
                "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{\"姓名\":\"张三\",\"照片\":\"TMP:old.png\"},\"reason\":\"r\"}");
        Application result = service.resubmit(100L, Map.of("照片", "TMP:new.png"), null, userOf(5L, "bob"));
        assertEquals(0, result.getStatus());
        assertTrue(result.getBizData().contains("TMP:new.png"));
        assertTrue(result.getBizData().contains("\"姓名\":\"张三\""));
        verify(photoStorageService).deleteTempPhoto("old.png");
    }

    /** CONTRACT 申请：照片字段仍被拒绝（合同仅身份证号/银行卡号） */
    @Test
    void contractResubmitRejectsPhotoField() {
        appOf("CONTRACT", "alice", 5L,
                "{\"contractId\":3,\"projectId\":2,\"expertName\":\"张三\",\"changes\":{\"身份证号\":\"110101199001010011\"},\"reason\":\"r\"}");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resubmit(100L, Map.of("照片", "TMP:new.png"), null, userOf(5L, "alice")));
        assertTrue(String.valueOf(ex.getMessage()).contains("合同"));
    }

    /** 非申请人拒绝；申请人改名（用户名不再匹配 applicant）但 applicant_id 匹配时仍可操作 */
    @Test
    void resubmitGuardsApplicantIdentityByIdOrName() {
        appOf("EXPERT_MODIFY", "alice", 5L,
                "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{\"姓名\":\"张三\"},\"reason\":\"r\"}");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resubmit(100L, Map.of("姓名", "李四"), null, userOf(9L, "carol")));
        assertTrue(String.valueOf(ex.getMessage()).contains("仅申请人本人"));
    }

    /** 申请人名字段值非法（非 TMP: 临时名）时拒绝 */
    @Test
    void resubmitRejectsNonTempPhotoValue() {
        appOf("EXPERT_MODIFY", "alice", 5L,
                "{\"expertId\":9,\"expertName\":\"张三\",\"changes\":{\"姓名\":\"张三\"},\"reason\":\"r\"}");
        assertThrows(BusinessException.class,
                () -> service.resubmit(100L, Map.of("照片", "avatar_9_x.png"), null, userOf(5L, "alice")));
    }
}
