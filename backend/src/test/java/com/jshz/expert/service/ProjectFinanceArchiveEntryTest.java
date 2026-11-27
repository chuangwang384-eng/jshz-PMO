package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.entity.ProjectStageForm;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.ProjectAssistantMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.mapper.ProjectExecutorMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import com.jshz.expert.mapper.ProjectStageFormMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合规立项申请表独立入口 + 服务端必填校验（2026-11-27 新需求 1 / 需求 3）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectFinanceArchiveEntryTest {
    static final Long PROJECT_ID = 1L;
    static final Long PHASE_ID = 99L;
    static final String FINANCE_PHASE = "会前财务资料上传";
    static final String ARCHIVE = "project_finance_fixture.xlsx";

    @InjectMocks ProjectService service;
    @Mock ProjectMapper projects;
    @Mock ProjectDocMapper docs;
    @Mock ProjectPhaseMapper phases;
    @Mock ProjectStageFormMapper stageForms;
    @Mock ProjectExecutorMapper executors;
    @Mock ProjectAssistantMapper assistants;
    @Mock SysUserMapper users;
    @Mock ProjectStorageService storage;
    @Mock OperationLogService audit;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    AuthUser member() {
        return new AuthUser(2L, "owner", false, false, Set.of(), Set.of());
    }

    AuthUser viewAllReader() {
        return new AuthUser(9L, "viewer", false, false, Set.of(), Set.of("project:view-all"));
    }

    Project project() {
        Project p = new Project();
        p.setId(PROJECT_ID);
        p.setName("联调会议");
        p.setOwnerId(2L);
        p.setPhaseId(PHASE_ID);
        p.setStatus("ACTIVE");
        p.setProjectType("PLATFORM");
        p.setMeetingStartDate(LocalDate.of(2026, 11, 21));
        p.setMeetingEndDate(LocalDate.of(2026, 11, 21));
        return p;
    }

    ProjectPhase phase() {
        ProjectPhase p = new ProjectPhase();
        p.setId(PHASE_ID);
        p.setName(FINANCE_PHASE);
        p.setSort(99);
        p.setIsActive(1);
        return p;
    }

    ProjectDoc archiveDoc() {
        ProjectDoc d = new ProjectDoc();
        d.setId(7L);
        d.setProjectId(PROJECT_ID);
        d.setPhaseId(PHASE_ID);
        d.setFileName("合规立项申请表.xlsx");
        d.setFilePath(ARCHIVE);
        d.setUploadBy("张三");
        d.setCreateTime(java.time.LocalDateTime.parse("2026-11-26T10:15:30"));
        d.setIsDeleted(0);
        return d;
    }

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"),
                ProjectDoc.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"),
                ProjectStageForm.class);
        ReflectionTestUtils.setField(service, "baseMapper", projects);
    }

    // ---------- 新需求 1：独立入口 ----------

    @Test
    void archiveEntryReturnsTheCurrentEffectiveArchiveForMembers() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(docs.selectList(any())).thenReturn(List.of(archiveDoc()));
        when(storage.resolveFile(any())).thenReturn(java.nio.file.Path.of("fixture.xlsx"));

        Map<String, Object> result = service.financeArchive(PROJECT_ID, PHASE_ID, member());

        assertEquals(true, result.get("archived"));
        assertEquals(true, result.get("previewable"));
        assertEquals(ARCHIVE, result.get("filePath"));
        assertEquals("合规立项申请表.xlsx", result.get("fileName"));
        assertEquals("张三", result.get("uploadBy"));
        assertNotNull(result.get("createTime"));
    }

    @Test
    void archiveEntryHidesTheFilePathFromNonParticipants() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());

        Map<String, Object> result = service.financeArchive(PROJECT_ID, PHASE_ID, viewAllReader());

        assertEquals(false, result.get("archived"));
        assertEquals(false, result.get("previewable"));
        assertFalse(result.containsKey("filePath"), "非项目成员不得拿到归档件路径");
    }

    @Test
    void archiveEntryReportsNotArchivedWhenNoFileExists() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(docs.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.financeArchive(PROJECT_ID, PHASE_ID, member());

        assertEquals(false, result.get("archived"));
        assertEquals(true, result.get("previewable"));
        assertNull(result.get("filePath"));
    }

    @Test
    void archiveEntryRequiresAReadableProject() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(executors.selectCount(any())).thenReturn(0L);
        when(assistants.selectCount(any())).thenReturn(0L);

        // 既非成员又无 view-all 豁免 → 不可读
        assertThrows(BusinessException.class,
                () -> service.financeArchive(PROJECT_ID, PHASE_ID, new AuthUser(77L, "nobody", false, false, Set.of(), Set.of())));
    }

    // ---------- 新需求 3：服务端必填校验 ----------

    @Test
    void missingRequiredFieldsAreReportedWithChineseLabels() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");

        List<String> missing = ProjectFinanceWorkbook.missingRequiredFields("PLATFORM", values);

        assertTrue(missing.contains("细分类型"), missing.toString());
        assertTrue(missing.contains("我方公司"), missing.toString());
        assertFalse(missing.contains("会议日期"), missing.toString());
        assertFalse(missing.contains("会议/项目名称"), missing.toString());
    }

    @Test
    void hengRuiCompanyMakesTheWholeSectionRequired() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("companyName", "江苏恒瑞医药");
        values.put("subType", "有合同");
        values.put("ourCompany", "景帅");
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");
        values.put("province", "江苏");

        List<String> missing = ProjectFinanceWorkbook.missingRequiredFields("DIRECT_PAY", values);

        assertTrue(missing.contains("事业部"), missing.toString());
        assertTrue(missing.contains("产品线"), missing.toString());
        assertTrue(missing.contains("记账部门"), missing.toString());
        assertTrue(missing.contains("SFA/HOPES/CRM/钉钉"), missing.toString());
        assertFalse(missing.contains("公司名称"), missing.toString());
        assertFalse(missing.contains("省区"), missing.toString());
    }

    @Test
    void otherCompanyOnlyNeedsCompanyNameAndProvince() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("companyName", "某医药公司");
        values.put("subType", "无合同");
        values.put("ourCompany", "景帅");
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");
        values.put("province", "江苏");

        List<String> missing = ProjectFinanceWorkbook.missingRequiredFields("DIRECT_PAY", values);

        assertTrue(missing.isEmpty(), missing.toString());
    }

    @Test
    void savingWithMissingRequiredFieldsIsRejectedByTheServer() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(projects.selectOne(any())).thenReturn(project());
        when(phases.selectById(PHASE_ID)).thenReturn(phase());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phaseId", PHASE_ID);
        // 缺「我方公司」与必填的主数据字段（后端会用项目主数据补 meetingName/meetingDate）
        body.put("formData", Map.of("subType", "单会立项"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.saveStageForm(PROJECT_ID, body, member()));

        assertTrue(e.getMessage().contains("我方公司"), e.getMessage());
        assertFalse(e.getMessage().contains("subType"), "错误信息只应暴露中文字段名：" + e.getMessage());
        verify(stageForms, never()).insert(any(ProjectStageForm.class));
        verify(storage, never()).saveGenerated(any(), any(), any());
    }

    @Test
    void savingWithAllRequiredFieldsProceeds() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(projects.selectOne(any())).thenReturn(project());
        when(phases.selectById(PHASE_ID)).thenReturn(phase());
        when(stageForms.selectOne(any())).thenReturn(null);
        when(docs.selectList(any())).thenReturn(List.of());
        when(storage.saveGenerated(any(), any(), any())).thenReturn(ARCHIVE);
        SysUser owner = new SysUser();
        owner.setId(2L);
        owner.setUsername("张三");
        when(users.selectById(2L)).thenReturn(owner);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phaseId", PHASE_ID);
        body.put("formData", Map.of("subType", "单会立项", "ourCompany", "河南景帅"));

        service.saveStageForm(PROJECT_ID, body, member());

        ArgumentCaptor<ProjectStageForm> captor = ArgumentCaptor.forClass(ProjectStageForm.class);
        verify(stageForms).insert(captor.capture());
        assertNotNull(captor.getValue().getFormData());
        verify(storage).saveGenerated(any(), any(), any());
    }
}
