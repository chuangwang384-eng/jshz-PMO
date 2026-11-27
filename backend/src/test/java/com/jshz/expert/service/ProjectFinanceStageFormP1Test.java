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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会前财务资料上传阶段 P1 整改回归（2026-11-26）：
 * <ul>
 *   <li>P1-9 阶段表单按项目类型字段白名单清洗：白名单外 key 丢弃、类型不符拒绝且只报中文字段名</li>
 *   <li>P1-12 归档替换批量软删同前缀旧件并清理旧物理文件（不再只替换 findFirst 命中的一条）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProjectFinanceStageFormP1Test {
    static final Long PROJECT_ID = 1L;
    static final Long PHASE_ID = 99L;
    static final String FINANCE_PHASE = "会前财务资料上传";
    static final String ARCHIVE_PREFIX = ProjectService.FINANCE_ARCHIVE_PREFIX;

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

    AuthUser owner() {
        return new AuthUser(2L, "owner", false, false, Set.of(), Set.of());
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

    ProjectDoc doc(String filePath) {
        ProjectDoc d = new ProjectDoc();
        d.setId(5L);
        d.setProjectId(PROJECT_ID);
        d.setPhaseId(PHASE_ID);
        d.setFileName("合规立项申请表.xlsx");
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

    // ---------- P1-9 字段白名单与类型校验 ----------

    @Test
    void fieldsOutsideTheProjectTypeWhitelistAreDropped() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("subType", "单会立项");
        raw.put("ourCompany", "河南景帅");
        raw.put("province", "河南");
        raw.put("__proto__", "x");
        raw.put("clientOwner", "王五");

        Map<String, Object> cleaned = ProjectFinanceWorkbook.sanitize("PLATFORM", raw);

        assertEquals("单会立项", cleaned.get("subType"));
        assertEquals("河南", cleaned.get("province"));
        assertFalse(cleaned.containsKey("__proto__"));
        // clientOwner 属于重点类字段，平台类必须丢弃
        assertFalse(cleaned.containsKey("clientOwner"));
    }

    @Test
    void directPayWhitelistKeepsItsOwnFieldsAndDropsPlatformOnes() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("subType", "有合同");
        raw.put("companyName", "恒瑞");
        raw.put("province", "江苏");
        raw.put("sponsorAmount", 100);
        raw.put("platform", "某学会");

        Map<String, Object> cleaned = ProjectFinanceWorkbook.sanitize("DIRECT_PAY", raw);

        assertEquals("恒瑞", cleaned.get("companyName"));
        assertFalse(cleaned.containsKey("sponsorAmount"));
        assertFalse(cleaned.containsKey("platform"));
    }

    @Test
    void amountMustBeANonNegativeNumber() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("sponsorAmount", "1000");

        BusinessException e = assertThrows(BusinessException.class,
                () -> ProjectFinanceWorkbook.sanitize("PLATFORM", raw));

        assertTrue(e.getMessage().contains("赞助金额"), e.getMessage());
        assertFalse(e.getMessage().contains("sponsorAmount"), e.getMessage());
    }

    @Test
    void textFieldsRejectNonTextValuesWithChineseLabels() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("province", 123);

        BusinessException e = assertThrows(BusinessException.class,
                () -> ProjectFinanceWorkbook.sanitize("PLATFORM", raw));

        assertTrue(e.getMessage().contains("省区"), e.getMessage());
        assertFalse(e.getMessage().contains("province"), e.getMessage());
    }

    @Test
    void blankProjectTypeIsRejectedWithGuidance() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> ProjectFinanceWorkbook.sanitize(null, new LinkedHashMap<>()));

        assertEquals("请先选择项目类型", e.getMessage());
    }

    @Test
    void clearedFieldsAreDroppedRatherThanStoredAsNull() {
        // 2026-11-26 P2-19：null / 空白串一律视为「未填写」，不落库
        // （表单为整包覆盖保存，不落 null 与不落 key 等价，但不再产生 null 值噪音）
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("subType", null);
        raw.put("ourCompany", "河南景帅");
        raw.put("sponsorAmount", "");

        Map<String, Object> cleaned = ProjectFinanceWorkbook.sanitize("PLATFORM", raw);

        assertFalse(cleaned.containsKey("subType"), "null 值不应落库");
        assertFalse(cleaned.containsKey("sponsorAmount"), "空白金额不应落库（避免写成文本单元格）");
        assertEquals("河南景帅", cleaned.get("ourCompany"));
    }

    @Test
    void savingTheStageFormPersistsOnlyWhitelistedFields() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(projects.selectOne(any())).thenReturn(project());
        when(phases.selectById(PHASE_ID)).thenReturn(phase());
        when(stageForms.selectOne(any())).thenReturn(null);
        when(docs.selectList(any())).thenReturn(List.of());
        when(storage.saveGenerated(any(), any(), any())).thenReturn(ARCHIVE_PREFIX + "new.xlsx");
        SysUser owner = new SysUser();
        owner.setId(2L);
        owner.setUsername("张三");
        when(users.selectById(2L)).thenReturn(owner);

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("subType", "单会立项");
        formData.put("ourCompany", "河南景帅");
        formData.put("remark", "正常备注");
        formData.put("__evil__", "x");
        formData.put("clientOwner", "王五");

        service.saveStageForm(PROJECT_ID, Map.of("phaseId", PHASE_ID, "formData", formData), owner());

        ArgumentCaptor<ProjectStageForm> captor = ArgumentCaptor.forClass(ProjectStageForm.class);
        verify(stageForms).insert(captor.capture());
        String json = captor.getValue().getFormData();
        assertNotNull(json);
        assertTrue(json.contains("单会立项"), json);
        assertTrue(json.contains("正常备注"), json);
        assertFalse(json.contains("__evil__"), json);
        assertFalse(json.contains("王五"), json);
    }

    // ---------- P1-12 归档替换批量软删 ----------

    @Test
    void everyPreviousArchiveOfThePhaseIsSoftDeletedAndCleanedUp() {
        when(docs.selectList(any())).thenReturn(List.of(
                doc(ARCHIVE_PREFIX + "old1.xlsx"),
                doc(ARCHIVE_PREFIX + "old2.xlsx")));
        when(storage.saveGenerated(any(), any(), any())).thenReturn(ARCHIVE_PREFIX + "new.xlsx");

        service.archiveStageDocument(PROJECT_ID, PHASE_ID, ARCHIVE_PREFIX, "xlsx", "合规立项申请表.xlsx",
                new byte[] { 1 }, owner(), true);

        verify(docs, org.mockito.Mockito.times(2)).update(isNull(), any());
        verify(storage).deleteFile(ARCHIVE_PREFIX + "old1.xlsx");
        verify(storage).deleteFile(ARCHIVE_PREFIX + "old2.xlsx");
        ArgumentCaptor<ProjectDoc> inserted = ArgumentCaptor.forClass(ProjectDoc.class);
        verify(docs).insert(inserted.capture());
        assertEquals(ARCHIVE_PREFIX + "new.xlsx", inserted.getValue().getFilePath());
        assertEquals(0, inserted.getValue().getIsDeleted());
    }

    @Test
    void archivesOfOtherPrefixesAreLeftUntouched() {
        when(docs.selectList(any())).thenReturn(List.of(doc("uploads/contract.pdf")));
        when(storage.saveGenerated(any(), any(), any())).thenReturn(ARCHIVE_PREFIX + "new.xlsx");

        service.archiveStageDocument(PROJECT_ID, PHASE_ID, ARCHIVE_PREFIX, "xlsx", "合规立项申请表.xlsx",
                new byte[] { 1 }, owner(), true);

        verify(docs, never()).update(isNull(), any());
        verify(storage, never()).deleteFile("uploads/contract.pdf");
    }
}
