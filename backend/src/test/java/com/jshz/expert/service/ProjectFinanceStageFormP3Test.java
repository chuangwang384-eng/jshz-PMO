package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.entity.ProjectStageForm;
import com.jshz.expert.mapper.ProjectAssistantMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.mapper.ProjectExecutorMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import com.jshz.expert.mapper.ProjectStageFormMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会前财务资料上传阶段 P3 整改回归（2026-11-26）：
 * <ul>
 *   <li>P3-22 单日会议日期按 Excel 原生日期写入（可排序/筛选/计算），跨日保留区间文本</li>
 *   <li>P3-23 阶段表单并发覆盖守卫（版本令牌不一致即拒绝写入）</li>
 *   <li>P3-25 财务表单「地点」改名消歧（与项目属性「地点」区分）</li>
 *   <li>P3-26 归档事务回滚时清理新生成的物理文件（不留磁盘孤儿）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProjectFinanceStageFormP3Test {
    static final Long PROJECT_ID = 1L;
    static final Long PHASE_ID = 99L;
    static final String FINANCE_PHASE = "会前财务资料上传";

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
        p.setMeetingStartDate(java.time.LocalDate.of(2026, 11, 21));
        p.setMeetingEndDate(java.time.LocalDate.of(2026, 11, 21));
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

    ProjectStageForm storedForm(String updateTime) {
        ProjectStageForm f = new ProjectStageForm();
        f.setId(3L);
        f.setProjectId(PROJECT_ID);
        f.setPhaseId(PHASE_ID);
        f.setFormData("{\"subType\":\"单会立项\"}");
        f.setUpdateTime(LocalDateTime.parse(updateTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return f;
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
    }

    // ---------- P3-22 会议日期原生日期写入 ----------

    @Test
    void singleDayMeetingDateIsWrittenAsNativeExcelDate() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subType", "单会立项");
        values.put("ourCompany", "河南景帅");
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");

        byte[] bytes = ProjectFinanceWorkbook.generate("PLATFORM", values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("平台类项目");
            Cell cell = sheet.getRow(2).getCell(7); // H 列 = 会议日期
            assertEquals(CellType.NUMERIC, cell.getCellType(), "单日会议日期应写成 Excel 原生日期");
            assertTrue(DateUtil.isCellDateFormatted(cell), "单元格应带日期格式，否则 Excel 显示序列号");
            assertEquals(java.time.LocalDate.of(2026, 11, 21), cell.getLocalDateTimeCellValue().toLocalDate());
        }
    }

    @Test
    void multiDayMeetingDateKeepsTheRangeText() throws Exception {
        // 单列装不下两个原生日期，跨日保留区间文本（不丢结束日）
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subType", "单会立项");
        values.put("ourCompany", "河南景帅");
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21 至 2026-11-23");

        byte[] bytes = ProjectFinanceWorkbook.generate("PLATFORM", values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("平台类项目");
            Cell cell = sheet.getRow(2).getCell(7);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("2026-11-21 至 2026-11-23", cell.getStringCellValue());
        }
    }

    @Test
    void keyTypeSheetWithoutDateFormatStillShowsADate() throws Exception {
        // 重点类的日期列无日期格式，需补格式否则显示序列号
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");

        byte[] bytes = ProjectFinanceWorkbook.generate("KEY", values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("重点类项目");
            Cell cell = sheet.getRow(2).getCell(2); // C 列 = 日期（重点类首个字段）
            assertEquals(CellType.NUMERIC, cell.getCellType());
            assertTrue(DateUtil.isCellDateFormatted(cell), "补了日期格式后不应显示序列号");
        }
    }

    // ---------- P3-23 并发覆盖守卫 ----------

    @Test
    void savingWithAStaleVersionIsRejected() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(projects.selectOne(any())).thenReturn(project());
        when(phases.selectById(PHASE_ID)).thenReturn(phase());
        when(stageForms.selectOne(any())).thenReturn(storedForm("2026-11-26 10:00:00"));
        when(docs.selectList(any())).thenReturn(List.of());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phaseId", PHASE_ID);
        body.put("formData", Map.of("subType", "单会立项", "ourCompany", "河南景帅",
                "meetingName", "联调会议", "meetingDate", "2026-11-21"));
        body.put("version", "2026-11-26 09:00:00");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.saveStageForm(PROJECT_ID, body, owner()));

        assertEquals(409, e.getCode());
        assertTrue(e.getMessage().contains("已被他人保存"), e.getMessage());
        verify(stageForms, never()).updateById(any(ProjectStageForm.class));
    }

    @Test
    void savingWithTheCurrentVersionSucceeds() {
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(projects.selectOne(any())).thenReturn(project());
        when(phases.selectById(PHASE_ID)).thenReturn(phase());
        when(stageForms.selectOne(any())).thenReturn(storedForm("2026-11-26 10:00:00"));
        when(docs.selectList(any())).thenReturn(List.of());
        when(storage.saveGenerated(any(), any(), any())).thenReturn("project_finance_fixture.xlsx");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phaseId", PHASE_ID);
        body.put("formData", Map.of("subType", "单会立项", "ourCompany", "河南景帅",
                "meetingName", "联调会议", "meetingDate", "2026-11-21"));
        body.put("version", "2026-11-26 10:00:00");

        service.saveStageForm(PROJECT_ID, body, owner());

        verify(stageForms).updateById(any(ProjectStageForm.class));
    }

    @Test
    void savingWithoutAVersionKeepsTheLegacyBehavior() {
        // 旧客户端不带 version：不做并发校验（保持兼容）
        when(projects.selectById(PROJECT_ID)).thenReturn(project());
        when(projects.selectOne(any())).thenReturn(project());
        when(phases.selectById(PHASE_ID)).thenReturn(phase());
        when(stageForms.selectOne(any())).thenReturn(storedForm("2026-11-26 10:00:00"));
        when(docs.selectList(any())).thenReturn(List.of());
        when(storage.saveGenerated(any(), any(), any())).thenReturn("project_finance_fixture.xlsx");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phaseId", PHASE_ID);
        body.put("formData", Map.of("subType", "单会立项", "ourCompany", "河南景帅",
                "meetingName", "联调会议", "meetingDate", "2026-11-21"));

        service.saveStageForm(PROJECT_ID, body, owner());

        verify(stageForms).updateById(any(ProjectStageForm.class));
    }

    // ---------- P3-25 地点改名消歧 ----------

    @Test
    void locationFieldIsDisambiguatedFromTheProjectCategory() {
        ProjectFinanceWorkbook.Field location = ProjectFinanceWorkbook.schemaOf("PLATFORM")
                .fields().stream().filter(f -> f.key().equals("location")).findFirst().orElse(null);
        assertNotNull(location);
        assertEquals("会议地点（具体地址）", location.label(),
                "财务表单的地点需与项目属性「地点（线上/线下）」区分");
    }

    // ---------- P3-26 归档回滚清理新文件 ----------

    @Test
    void archivingRollsBackAndCleansTheNewFile() {
        when(docs.selectList(any())).thenReturn(List.of());
        when(storage.saveGenerated(any(), any(), any())).thenReturn("project_finance_new.xlsx");

        // 模拟事务上下文：注册回滚清理钩子
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.archiveStageDocument(PROJECT_ID, PHASE_ID, ProjectService.FINANCE_ARCHIVE_PREFIX,
                    "xlsx", "合规立项申请表.xlsx", new byte[] { 1 }, owner(), true);

            // 触发回滚
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations().forEach(s -> s.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storage).deleteFile("project_finance_new.xlsx");
    }

    @Test
    void archivingOnCommitKeepsTheNewFile() {
        when(docs.selectList(any())).thenReturn(List.of());
        when(storage.saveGenerated(any(), any(), any())).thenReturn("project_finance_new.xlsx");

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.archiveStageDocument(PROJECT_ID, PHASE_ID, ProjectService.FINANCE_ARCHIVE_PREFIX,
                    "xlsx", "合规立项申请表.xlsx", new byte[] { 1 }, owner(), true);

            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations().forEach(s -> s.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storage, never()).deleteFile("project_finance_new.xlsx");
        ArgumentCaptor<ProjectDoc> inserted = ArgumentCaptor.forClass(ProjectDoc.class);
        verify(docs).insert(inserted.capture());
        assertEquals("project_finance_new.xlsx", inserted.getValue().getFilePath());
        assertEquals(PROJECT_ID, inserted.getValue().getProjectId());
        assertEquals(0, inserted.getValue().getIsDeleted());
        verify(docs, never()).update(isNull(), any());
    }
}
