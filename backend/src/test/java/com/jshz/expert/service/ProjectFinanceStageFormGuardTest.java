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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会前财务资料上传阶段 P0 整改回归（2026-11-26）：
 * <ul>
 *   <li>P0-1 自动归档的《合规立项申请表》禁止从阶段资料里删除（否则阶段推进被卡住）</li>
 *   <li>P0-3 未选项目类型时必填项先报根因「选择项目类型」</li>
 *   <li>P0-4 阶段表单读取对非真实项目参与人脱敏；归档件对非参与者不可见/不可下载</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProjectFinanceStageFormGuardTest {
    static final Long PROJECT_ID = 1L;
    static final Long PHASE_ID = 99L;
    static final String FINANCE_PHASE = "会前财务资料上传";
    static final String ARCHIVE_PATH = ProjectService.FINANCE_ARCHIVE_PREFIX + "fixture.xlsx";

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

    /** 仅有全量查看豁免、并非项目真实参与人的读者 */
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
        return p;
    }

    ProjectDoc doc(String filePath) {
        ProjectDoc d = new ProjectDoc();
        d.setId(5L);
        d.setProjectId(PROJECT_ID);
        d.setPhaseId(PHASE_ID);
        d.setFileName(filePath == null ? "附件.pdf" : "合规立项申请表.xlsx");
        d.setFilePath(filePath);
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

    // ---------- P0-1 归档件禁止删除 ----------

    @Test
    void financeArchiveCannotBeDeletedFromStageMaterials() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(docs.selectById(5L)).thenReturn(doc(ARCHIVE_PATH));

        BusinessException e = assertThrows(BusinessException.class, () -> service.deleteDoc(PROJECT_ID, 5L, member()));

        assertTrue(e.getMessage().contains("合规立项申请表"));
        verify(docs, never()).updateById(any(ProjectDoc.class));
    }

    @Test
    void ordinaryStageAttachmentCanStillBeDeleted() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(docs.selectById(5L)).thenReturn(doc("uploads/contract.pdf"));

        service.deleteDoc(PROJECT_ID, 5L, member());

        verify(docs).updateById(any(ProjectDoc.class));
    }

    // ---------- P0-3 必填项先报根因 ----------

    @Test
    void missingProjectTypeIsReportedBeforeTheFinanceForm() {
        Project p = project();
        p.setProjectType(null);

        List<String> missing = service.missingRequiredTemplates(p, PHASE_ID, FINANCE_PHASE);

        assertEquals(List.of("选择项目类型"), missing);
    }

    @Test
    void financeFormIsRequiredOnceTheProjectTypeIsKnown() {
        when(docs.selectList(any())).thenReturn(List.of());

        List<String> missing = service.missingRequiredTemplates(project(), PHASE_ID, FINANCE_PHASE);

        assertEquals(List.of("保存阶段表单"), missing);
    }

    @Test
    void archivedFinanceFormSatisfiesTheStageRequirement() {
        when(docs.selectList(any())).thenReturn(List.of(doc(ARCHIVE_PATH)));
        when(storage.resolveFile(any())).thenReturn(Path.of("fixture.xlsx"));

        List<String> missing = service.missingRequiredTemplates(project(), PHASE_ID, FINANCE_PHASE);

        assertTrue(missing.isEmpty());
    }

    // ---------- P0-4 读取脱敏 ----------

    @Test
    void stageFormIsMaskedForReadersWhoAreNotProjectParticipants() {
        givenStageForm();
        when(projects.selectById(PROJECT_ID)).thenReturn(project());

        Map<String, Object> result = service.getStageForm(PROJECT_ID, PHASE_ID, viewAllReader());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) result.get("formData");

        assertNotNull(fields);
        assertEquals("张*", fields.get("ourOwner"));
        assertEquals("李*", fields.get("clientContact"));
        assertEquals("***", fields.get("sfaNo"));
        assertEquals("***", fields.get("bookDept"));
        assertEquals("郑州华秀", fields.get("ourCompany"));
    }

    @Test
    void projectParticipantsStillSeeTheRawStageForm() {
        givenStageForm();
        when(projects.selectById(PROJECT_ID)).thenReturn(project());

        Map<String, Object> result = service.getStageForm(PROJECT_ID, PHASE_ID, member());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) result.get("formData");

        assertNotNull(fields);
        assertEquals("张三", fields.get("ourOwner"));
        assertEquals("李四", fields.get("clientContact"));
        assertEquals("SFA-8899", fields.get("sfaNo"));
    }

    @Test
    void financeArchiveIsHiddenFromReadersWhoAreNotProjectParticipants() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(docs.selectList(any())).thenReturn(List.of(doc(ARCHIVE_PATH), doc("uploads/contract.pdf")));
        when(phases.selectById(PHASE_ID)).thenReturn(phase());

        List<Map<String, Object>> visible = service.listDocs(PROJECT_ID, null, viewAllReader());

        assertEquals(1, visible.size());
        assertEquals("uploads/contract.pdf", visible.get(0).get("filePath"));
    }

    @Test
    void financeArchiveIsVisibleToProjectParticipants() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(docs.selectList(any())).thenReturn(List.of(doc(ARCHIVE_PATH)));
        when(phases.selectById(PHASE_ID)).thenReturn(phase());

        List<Map<String, Object>> visible = service.listDocs(PROJECT_ID, null, member());

        assertEquals(1, visible.size());
        assertEquals(ARCHIVE_PATH, visible.get(0).get("filePath"));
    }

    @Test
    void financeArchiveCannotBeDownloadedByReadersWhoAreNotProjectParticipants() {
        when(docs.selectOne(any())).thenReturn(doc(ARCHIVE_PATH));
        when(projects.selectById(PROJECT_ID)).thenReturn(project());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.requireArchivedFileReadable(ARCHIVE_PATH, viewAllReader()));

        assertEquals(403, e.getCode());
    }

    @Test
    void financeArchiveRemainsDownloadableForProjectParticipants() {
        when(docs.selectOne(any())).thenReturn(doc(ARCHIVE_PATH));
        when(projects.selectById(PROJECT_ID)).thenReturn(project());

        service.requireArchivedFileReadable(ARCHIVE_PATH, member());
    }

    // ---------- 工具 ----------

    ProjectPhase phase() {
        ProjectPhase p = new ProjectPhase();
        p.setId(PHASE_ID);
        p.setName(FINANCE_PHASE);
        p.setSort(99);
        p.setIsActive(1);
        return p;
    }

    void givenStageForm() {
        ProjectStageForm form = new ProjectStageForm();
        form.setId(3L);
        form.setProjectId(PROJECT_ID);
        form.setPhaseId(PHASE_ID);
        form.setFormData("{\"ourCompany\":\"郑州华秀\",\"clientContact\":\"李四\",\"sfaNo\":\"SFA-8899\","
                + "\"bookDept\":\"财务一部\",\"province\":\"河南\",\"remark\":\"正常备注\"}");
        when(stageForms.selectOne(any())).thenReturn(form);
        when(phases.selectById(PHASE_ID)).thenReturn(phase());
        SysUser owner = new SysUser();
        owner.setId(2L);
        owner.setUsername("张三");
        when(users.selectById(2L)).thenReturn(owner);
    }

    @Test
    void maskHelperKeepsBusinessFieldsAndHidesSensitiveOnes() {
        Map<String, Object> fields = new java.util.LinkedHashMap<>(Map.of(
                "ourOwner", "张三", "clientContact", "李四", "clientOwner", "王五",
                "sfaNo", "SFA-8899", "bookDept", "财务一部", "clientWorkUnit", "某医院",
                "sponsorCompany", "某药企", "province", "河南"));

        ProjectService.maskFinanceStageForm(fields);

        assertEquals("张*", fields.get("ourOwner"));
        assertEquals("李*", fields.get("clientContact"));
        assertEquals("王*", fields.get("clientOwner"));
        assertEquals("***", fields.get("sfaNo"));
        assertEquals("***", fields.get("bookDept"));
        assertEquals("***", fields.get("clientWorkUnit"));
        assertEquals("***", fields.get("sponsorCompany"));
        assertEquals("河南", fields.get("province"));
        assertFalse(fields.containsKey("absentField"));
    }
}
