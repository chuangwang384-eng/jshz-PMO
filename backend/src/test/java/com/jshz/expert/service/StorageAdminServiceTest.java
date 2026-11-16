package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.ComplianceExecution;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.CustomerAttachment;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.KnowledgeDocVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierImage;
import com.jshz.expert.entity.ProjectContract;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectSchedule;
import com.jshz.expert.mapper.ComplianceExecutionEventMapper;
import com.jshz.expert.mapper.ComplianceExecutionMapper;
import com.jshz.expert.mapper.ComplianceExecutionReminderMapper;
import com.jshz.expert.mapper.ComplianceExecutionTaskMapper;
import com.jshz.expert.mapper.ComplianceItemAssigneeMapper;
import com.jshz.expert.mapper.ComplianceItemHideMapper;
import com.jshz.expert.mapper.ComplianceItemHistoryMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.ComplianceProjectDocBindingMapper;
import com.jshz.expert.mapper.ComplianceProjectTodoMapper;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.ComplianceUserProgressMapper;
import com.jshz.expert.mapper.CustomerAttachmentMapper;
import com.jshz.expert.mapper.ExpertMapper;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.SupplierImageMapper;
import com.jshz.expert.mapper.SupplierMapper;
import com.jshz.expert.mapper.ProjectAssistantMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.mapper.ProjectExecutorMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseHistoryMapper;
import com.jshz.expert.mapper.ProjectScheduleMapper;
import com.jshz.expert.mapper.ProjectSpeakerMapper;
import com.jshz.expert.mapper.ProjectSupplierMapper;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 存储管理与硬删除工具单测：孤儿扫描判定、expected 校验、项目硬删护栏与级联（Owner 2026-11-12）。 */
class StorageAdminServiceTest {

    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final ProjectDocMapper docMapper = mock(ProjectDocMapper.class);
    private final ProjectScheduleMapper scheduleMapper = mock(ProjectScheduleMapper.class);
    private final ProjectContractMapper contractMapper = mock(ProjectContractMapper.class);
    private final ProjectSpeakerMapper speakerMapper = mock(ProjectSpeakerMapper.class);
    private final ProjectExpertMapper expertRelMapper = mock(ProjectExpertMapper.class);
    private final ProjectSupplierMapper supplierRelMapper = mock(ProjectSupplierMapper.class);
    private final ProjectPhaseHistoryMapper phaseHistoryMapper = mock(ProjectPhaseHistoryMapper.class);
    private final ProjectExecutorMapper executorMapper = mock(ProjectExecutorMapper.class);
    private final ProjectAssistantMapper assistantMapper = mock(ProjectAssistantMapper.class);
    private final ComplianceProjectTodoMapper todoMapper = mock(ComplianceProjectTodoMapper.class);
    private final ComplianceProjectDocBindingMapper bindingMapper = mock(ComplianceProjectDocBindingMapper.class);
    private final ComplianceExecutionMapper executionMapper = mock(ComplianceExecutionMapper.class);
    private final ComplianceExecutionTaskMapper executionTaskMapper = mock(ComplianceExecutionTaskMapper.class);
    private final ComplianceExecutionEventMapper executionEventMapper = mock(ComplianceExecutionEventMapper.class);
    private final ComplianceExecutionReminderMapper executionReminderMapper = mock(ComplianceExecutionReminderMapper.class);
    private final ComplianceItemMapper itemMapper = mock(ComplianceItemMapper.class);
    private final ComplianceItemAssigneeMapper itemAssigneeMapper = mock(ComplianceItemAssigneeMapper.class);
    private final ComplianceItemHideMapper itemHideMapper = mock(ComplianceItemHideMapper.class);
    private final ComplianceItemHistoryMapper itemHistoryMapper = mock(ComplianceItemHistoryMapper.class);
    private final ComplianceUserProgressMapper userProgressMapper = mock(ComplianceUserProgressMapper.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeDocVersionMapper knowledgeDocVersionMapper = mock(KnowledgeDocVersionMapper.class);
    private final ExpertMapper expertMapper = mock(ExpertMapper.class);
    private final ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
    private final CustomerAttachmentMapper customerAttachmentMapper = mock(CustomerAttachmentMapper.class);
    private final SupplierImageMapper supplierImageMapper = mock(SupplierImageMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final ProjectStorageService storage = mock(ProjectStorageService.class);
    private final OperationLogService opLog = mock(OperationLogService.class);

    private StorageAdminService service;

    private final AuthUser admin = new AuthUser(1L, "admin", true, false, Set.of(), Set.of("storage:manage"));

    @TempDir
    Path projectDir;
    @TempDir
    Path previewDir;
    @TempDir
    Path knowledgeDir;
    @TempDir
    Path photoDir;
    @TempDir
    Path photoTmpDir;
    @TempDir
    Path customerDir;
    @TempDir
    Path supplierDir;

    @BeforeEach
    void setUp() {
        service = new StorageAdminService(projects, docMapper, scheduleMapper, contractMapper,
                speakerMapper, expertRelMapper, supplierRelMapper, phaseHistoryMapper,
                executorMapper, assistantMapper, todoMapper, bindingMapper,
                executionMapper, executionTaskMapper, executionEventMapper, executionReminderMapper,
                itemMapper, itemAssigneeMapper, itemHideMapper, itemHistoryMapper, userProgressMapper,
                knowledgeDocumentMapper, knowledgeDocVersionMapper, expertMapper, applicationMapper,
                customerAttachmentMapper, supplierImageMapper, supplierMapper,
                storage, opLog);
        ReflectionTestUtils.setField(service, "projectDir", projectDir.toString());
        ReflectionTestUtils.setField(service, "knowledgeDir", knowledgeDir.toString());
        ReflectionTestUtils.setField(service, "previewDir", previewDir.toString());
        ReflectionTestUtils.setField(service, "photoDir", photoDir.toString());
        ReflectionTestUtils.setField(service, "photoTmpDir", photoTmpDir.toString());
        ReflectionTestUtils.setField(service, "customerDir", customerDir.toString());
        ReflectionTestUtils.setField(service, "supplierDir", supplierDir.toString());
        ReflectionTestUtils.setField(service, "warnThresholdBytes", 1024L);
        ReflectionTestUtils.setField(service, "criticalThresholdBytes", 4096L);
    }

    private ProjectDoc doc(String filePath) {
        ProjectDoc d = new ProjectDoc();
        d.setFilePath(filePath);
        return d;
    }

    // ==================== 统计与预警 ====================

    @Test
    void statsReportsDirsAndLevel() throws Exception {
        Files.writeString(projectDir.resolve("a.txt"), "12345");
        Files.createDirectories(previewDir);
        Files.writeString(previewDir.resolve("c.pdf"), "12345678");

        Map<String, Object> result = service.stats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dirs = (List<Map<String, Object>>) result.get("dirs");
        assertEquals(7, dirs.size());
        // 预览缓存 8 字节 > 1024? 否——8 < 1024 NONE；项目资料库 5 字节 NONE
        assertEquals("NONE", result.get("level"));
        assertTrue(((Long) result.get("totalBytes")) > 0);
        // 阈值极小时升级为 WARNING
        ReflectionTestUtils.setField(service, "warnThresholdBytes", 3L);
        ReflectionTestUtils.setField(service, "criticalThresholdBytes", 100L);
        assertEquals("WARNING", service.stats().get("level"));
        ReflectionTestUtils.setField(service, "warnThresholdBytes", 1L);
        ReflectionTestUtils.setField(service, "criticalThresholdBytes", 5L);
        assertEquals("CRITICAL", service.stats().get("level"));
    }

    // ==================== 孤儿文件 ====================

    @Test
    void orphanScanFindsOnlyUnreferenced() throws Exception {
        Files.writeString(projectDir.resolve("referenced-doc.pdf"), "x");
        Files.writeString(projectDir.resolve("referenced-schedule.xlsx"), "x");
        Files.writeString(projectDir.resolve("referenced-contract.pdf"), "x");
        Files.writeString(projectDir.resolve("orphan.docx"), "orphan-data");
        Files.createDirectories(projectDir.resolve("_richtext"));
        Files.writeString(projectDir.resolve("_richtext").resolve("used.json"), "{}");
        Files.writeString(projectDir.resolve("_richtext").resolve("orphan.json"), "{}");

        when(docMapper.selectList(isNull())).thenReturn(List.of(
                doc("referenced-doc.pdf"), doc("_richtext/used.json")));
        when(scheduleMapper.selectList(isNull())).thenReturn(List.of(sched("referenced-schedule.xlsx")));
        when(contractMapper.selectList(isNull())).thenReturn(List.of(contract("referenced-contract.pdf")));

        Map<String, Object> result = service.projectOrphans();
        assertEquals(2, result.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        Set<String> paths = items.stream().map(m -> (String) m.get("path")).collect(java.util.stream.Collectors.toSet());
        assertTrue(paths.contains("orphan.docx"));
        assertTrue(paths.contains("_richtext/orphan.json"));
    }

    private ProjectSchedule sched(String filePath) {
        ProjectSchedule s = new ProjectSchedule();
        s.setFilePath(filePath);
        return s;
    }

    private ProjectContract contract(String filePath) {
        ProjectContract c = new ProjectContract();
        c.setFilePath(filePath);
        return c;
    }

    @Test
    void orphanPurgeCountMismatchRejected() throws Exception {
        Files.writeString(projectDir.resolve("orphan.docx"), "data");
        when(docMapper.selectList(isNull())).thenReturn(List.of());
        when(scheduleMapper.selectList(isNull())).thenReturn(List.of());
        when(contractMapper.selectList(isNull())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.purgeProjectOrphans(5L, null, admin));
        assertTrue(ex.getMessage().contains("已变化"));
        verify(storage, never()).deleteFile(any());
    }

    @Test
    void orphanPurgeDeletesOnlyOrphans() throws Exception {
        Files.writeString(projectDir.resolve("referenced.pdf"), "keep");
        Files.writeString(projectDir.resolve("orphan1.docx"), "12345");
        Files.writeString(projectDir.resolve("orphan2.docx"), "1234567");
        when(docMapper.selectList(isNull())).thenReturn(List.of(doc("referenced.pdf")));
        when(scheduleMapper.selectList(isNull())).thenReturn(List.of());
        when(contractMapper.selectList(isNull())).thenReturn(List.of());

        Map<String, Object> result = service.purgeProjectOrphans(2L, 12L, admin);
        assertEquals(2, result.get("deleted"));
        assertEquals(12L, result.get("deletedBytes"));
        assertFalse(Files.exists(projectDir.resolve("orphan1.docx")));
        assertFalse(Files.exists(projectDir.resolve("orphan2.docx")));
        assertTrue(Files.exists(projectDir.resolve("referenced.pdf")));
        verify(opLog).record(anyLong(), any(), eq(OperationLogService.ACTION_STORAGE_PURGE_ORPHANS), any(), eq("STORAGE"), eq("project"), isNull());
    }

    // ==================== 已删除资料的文件回收（project-deleted） ====================

    @Test
    void softDeletedDocFilesAreReclaimableButSharedFilesAreKept() throws Exception {
        Files.writeString(projectDir.resolve("del_only.pdf"), "12345");
        Files.writeString(projectDir.resolve("shared.pdf"), "x");
        Files.writeString(projectDir.resolve("active.pdf"), "x");
        Files.writeString(projectDir.resolve("sched.pdf"), "x");

        ProjectDoc delOnly = new ProjectDoc();
        delOnly.setFilePath("del_only.pdf");
        delOnly.setIsDeleted(1);
        ProjectDoc sharedDeleted = new ProjectDoc();
        sharedDeleted.setFilePath("shared.pdf");
        sharedDeleted.setIsDeleted(1);
        ProjectDoc sharedActive = new ProjectDoc();
        sharedActive.setFilePath("shared.pdf");
        sharedActive.setIsDeleted(0);
        ProjectDoc active = new ProjectDoc();
        active.setFilePath("active.pdf");
        active.setIsDeleted(0);
        when(docMapper.selectList(isNull())).thenReturn(List.of(delOnly, sharedDeleted, sharedActive, active));

        ProjectSchedule sched = new ProjectSchedule();
        sched.setFilePath("sched.pdf");
        when(scheduleMapper.selectList(isNull())).thenReturn(List.of(sched));
        when(contractMapper.selectList(isNull())).thenReturn(List.of());

        // 普通孤儿口径：软删行也算引用 → 0 个（这正是用户看到的"清理后仍有文件"的原因）
        assertEquals(0, service.orphansOf("project").get("count"));

        // 回收口径：仅被软删行引用且无未删引用的文件才计入
        Map<String, Object> recycle = service.orphansOf("project-deleted");
        assertEquals(1, recycle.get("count"));
        assertEquals(5L, recycle.get("bytes"));

        Map<String, Object> purged = service.purgeOrphans("project-deleted", 1L, 5L, admin);
        assertEquals(1, purged.get("deleted"));
        assertFalse(Files.exists(projectDir.resolve("del_only.pdf")));
        assertTrue(Files.exists(projectDir.resolve("shared.pdf")));
        assertTrue(Files.exists(projectDir.resolve("active.pdf")));
        assertTrue(Files.exists(projectDir.resolve("sched.pdf")));
    }

    @Test
    void softDeletedReclaimIgnoresMissingAndNullIsDeletedRows() throws Exception {
        Files.writeString(projectDir.resolve("active_only.pdf"), "x");
        ProjectDoc legacy = new ProjectDoc();
        legacy.setFilePath("active_only.pdf");
        legacy.setIsDeleted(null); // 历史行 is_deleted 为 NULL，按未删处理
        when(docMapper.selectList(isNull())).thenReturn(List.of(legacy));
        when(scheduleMapper.selectList(isNull())).thenReturn(List.of());
        when(contractMapper.selectList(isNull())).thenReturn(List.of());

        assertEquals(0, service.orphansOf("project-deleted").get("count"));
    }

    // ==================== 全目录孤儿（知识/照片/临时/客户/供应商） ====================

    @Test
    void knowledgeOrphansExcludePreviewAndKeepVersionFiles() throws Exception {
        Files.writeString(knowledgeDir.resolve("doc_current.pdf"), "x");
        Files.writeString(knowledgeDir.resolve("doc_v1.pdf"), "x");
        Files.writeString(knowledgeDir.resolve("orphan.pdf"), "12345");
        Files.createDirectories(knowledgeDir.resolve("_preview"));
        Files.writeString(knowledgeDir.resolve("_preview").resolve("cache1.pdf"), "cache");

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFilePath("doc_current.pdf");
        KnowledgeDocVersion v1 = new KnowledgeDocVersion();
        v1.setFilePath("doc_v1.pdf");
        when(knowledgeDocumentMapper.selectList(isNull())).thenReturn(List.of(doc));
        when(knowledgeDocVersionMapper.selectList(isNull())).thenReturn(List.of(v1));

        Map<String, Object> scan = service.orphansOf("knowledge");
        assertEquals(1, scan.get("count"));

        Map<String, Object> purged = service.purgeOrphans("knowledge", 1L, 5L, admin);
        assertEquals(1, purged.get("deleted"));
        assertFalse(Files.exists(knowledgeDir.resolve("orphan.pdf")));
        assertTrue(Files.exists(knowledgeDir.resolve("_preview").resolve("cache1.pdf")));
        assertTrue(Files.exists(knowledgeDir.resolve("doc_v1.pdf")));
    }

    @Test
    void photoOrphansExcludeRichtextImages() throws Exception {
        Files.writeString(photoDir.resolve("avatar_1_100.png"), "x");
        Files.writeString(photoDir.resolve("richtext_100.png"), "inline");
        Files.writeString(photoDir.resolve("orphan.jpg"), "77");

        Expert e = new Expert();
        e.setPhoto("avatar_1_100.png");
        when(expertMapper.selectList(isNull())).thenReturn(List.of(e));

        Map<String, Object> scan = service.orphansOf("photo");
        assertEquals(1, scan.get("count"));
        assertEquals(2L, scan.get("bytes"));
    }

    @Test
    void photoTmpOrphansUseApplicationBizData() throws Exception {
        Files.writeString(photoTmpDir.resolve("tmp_a.png"), "x");
        Files.writeString(photoTmpDir.resolve("tmp_b.png"), "orphan");

        Application app = new Application();
        app.setBizData("{\"照片\":\"TMP:tmp_a.png\",\"身份证正面\":\"TMP:tmp_a.png\"}");
        when(applicationMapper.selectList(isNull())).thenReturn(List.of(app));

        Map<String, Object> scan = service.orphansOf("photo-tmp");
        assertEquals(1, scan.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) scan.get("items");
        assertEquals("tmp_b.png", items.get(0).get("path"));
    }

    @Test
    void customerAndSupplierOrphans() throws Exception {
        Files.writeString(customerDir.resolve("cust_a.pdf"), "x");
        Files.writeString(customerDir.resolve("cust_b.pdf"), "orphan");
        Files.writeString(supplierDir.resolve("supplier_a.jpg"), "x");
        Files.writeString(supplierDir.resolve("supplier_b.jpg"), "x");
        Files.writeString(supplierDir.resolve("supplier_c.jpg"), "orphan");

        CustomerAttachment att = new CustomerAttachment();
        att.setStorageName("cust_a.pdf");
        when(customerAttachmentMapper.selectList(isNull())).thenReturn(List.of(att));

        SupplierImage img = new SupplierImage();
        img.setFileName("supplier_a.jpg");
        when(supplierImageMapper.selectList(isNull())).thenReturn(List.of(img));
        Supplier legacy = new Supplier();
        legacy.setImages("[\"supplier_b.jpg\"]");
        when(supplierMapper.selectList(isNull())).thenReturn(List.of(legacy));

        assertEquals(1, service.orphansOf("customer").get("count"));
        assertEquals(1, service.orphansOf("supplier").get("count"));
    }

    @Test
    void allOrphansAggregatesAndUnknownScopeRejected() throws Exception {
        Files.writeString(projectDir.resolve("p_orphan.pdf"), "x");
        Files.writeString(knowledgeDir.resolve("k_orphan.pdf"), "x");
        when(docMapper.selectList(isNull())).thenReturn(List.of());
        when(scheduleMapper.selectList(isNull())).thenReturn(List.of());
        when(contractMapper.selectList(isNull())).thenReturn(List.of());
        when(knowledgeDocumentMapper.selectList(isNull())).thenReturn(List.of());
        when(knowledgeDocVersionMapper.selectList(isNull())).thenReturn(List.of());
        when(expertMapper.selectList(isNull())).thenReturn(List.of());
        when(applicationMapper.selectList(isNull())).thenReturn(List.of());
        when(customerAttachmentMapper.selectList(isNull())).thenReturn(List.of());
        when(supplierImageMapper.selectList(isNull())).thenReturn(List.of());
        when(supplierMapper.selectList(isNull())).thenReturn(List.of());

        Map<String, Object> all = service.allOrphans();
        assertEquals(7, ((List<?>) all.get("scopes")).size());
        assertEquals(2L, ((Number) all.get("totalCount")).longValue());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.orphansOf("unknown"));
        assertTrue(ex.getMessage().contains("未知的存储范围"));
    }

    // ==================== 预览缓存 ====================

    @Test
    void previewCachePurge() throws Exception {
        Files.createDirectories(previewDir);
        Files.writeString(previewDir.resolve("doc_1_v1.pdf"), "pdf");
        Files.writeString(previewDir.resolve("proj_x.pdf"), "pdf2");

        Map<String, Object> result = service.clearPreviewCache(2L, admin);
        assertEquals(2, result.get("deleted"));
        assertEquals(0, Files.list(previewDir).count());
        verify(opLog).record(anyLong(), any(), eq(OperationLogService.ACTION_STORAGE_CLEAR_PREVIEW), any(), eq("STORAGE"), any(), isNull());
    }

    @Test
    void previewCacheCountMismatchRejected() throws Exception {
        Files.createDirectories(previewDir);
        Files.writeString(previewDir.resolve("doc_1_v1.pdf"), "pdf");
        assertThrows(BusinessException.class, () -> service.clearPreviewCache(9L, admin));
        assertTrue(Files.exists(previewDir.resolve("doc_1_v1.pdf")));
    }

    // ==================== 项目硬删除 ====================

    private Project canceledProject() {
        Project p = new Project();
        p.setId(7L);
        p.setName("测试会展项目");
        p.setStatus(ProjectService.STATUS_CANCELED);
        return p;
    }

    @Test
    void purgeProjectRejectsWrongNameAndStatus() {
        when(projects.selectById(7L)).thenReturn(canceledProject());
        BusinessException nameEx = assertThrows(BusinessException.class,
                () -> service.purgeProject(7L, "错误名称", admin));
        assertTrue(nameEx.getMessage().contains("确认不符"));

        Project active = canceledProject();
        active.setStatus(ProjectService.STATUS_ACTIVE);
        when(projects.selectById(7L)).thenReturn(active);
        BusinessException statusEx = assertThrows(BusinessException.class,
                () -> service.purgeProject(7L, "测试会展项目", admin));
        assertTrue(statusEx.getMessage().contains("已取消"));

        when(projects.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.purgeProject(99L, "任意", admin));
    }

    @Test
    void purgeProjectCascadesAllTablesAndFiles() {
        when(projects.selectById(7L)).thenReturn(canceledProject());
        ProjectDoc d = doc("project_file1.pdf");
        ProjectSchedule s = sched("project_sched1.xlsx");
        ProjectContract c = contract("project_contract1.pdf");
        when(docMapper.selectList(any())).thenReturn(List.of(d));
        when(scheduleMapper.selectList(any())).thenReturn(List.of(s));
        when(contractMapper.selectList(any())).thenReturn(List.of(c));

        ComplianceExecution execution = new ComplianceExecution();
        execution.setId(5L);
        when(executionMapper.selectList(any())).thenReturn(List.of(execution));
        ComplianceItem item = new ComplianceItem();
        item.setId(9L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(projects.deleteById(7L)).thenReturn(1);

        Map<String, Object> result = service.purgeProject(7L, "测试会展项目", admin);

        assertEquals("测试会展项目", result.get("projectName"));
        assertEquals(1, result.get("projectRows"));
        // 级联删除全部 21 张表
        verify(executionTaskMapper).delete(any());
        verify(executionEventMapper).delete(any());
        verify(executionReminderMapper).delete(any());
        verify(executionMapper).delete(any());
        verify(itemAssigneeMapper).delete(any());
        verify(itemHideMapper).delete(any());
        verify(itemHistoryMapper).delete(any());
        verify(userProgressMapper).delete(any());
        verify(itemMapper).delete(any());
        verify(docMapper).delete(any());
        verify(scheduleMapper).delete(any());
        verify(speakerMapper).delete(any());
        verify(expertRelMapper).delete(any());
        verify(supplierRelMapper).delete(any());
        verify(contractMapper).delete(any());
        verify(phaseHistoryMapper).delete(any());
        verify(executorMapper).delete(any());
        verify(assistantMapper).delete(any());
        verify(todoMapper).delete(any());
        verify(bindingMapper).delete(any());
        verify(projects).deleteById(7L);
        // 物理文件：引用复核后删除（mock selectCount 默认 0 = 无引用）
        verify(storage).deleteFile("project_file1.pdf");
        verify(storage).deleteFile("project_sched1.xlsx");
        verify(storage).deleteFile("project_contract1.pdf");
        verify(opLog).record(anyLong(), any(), eq(OperationLogService.ACTION_STORAGE_PURGE_PROJECT), any(), eq("PROJECT"), eq(7L), isNull());
    }

    @Test
    void purgeProjectSkipsFileStillReferencedByOthers() {
        when(projects.selectById(7L)).thenReturn(canceledProject());
        when(docMapper.selectList(any())).thenReturn(List.of(doc("shared.pdf")));
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(contractMapper.selectList(any())).thenReturn(List.of());
        when(executionMapper.selectList(any())).thenReturn(List.of());
        when(itemMapper.selectList(any())).thenReturn(List.of());
        // 复核时另一项目仍引用 shared.pdf
        when(docMapper.selectCount(any())).thenReturn(1L);

        service.purgeProject(7L, "测试会展项目", admin);

        verify(storage, never()).deleteFile(eq("shared.pdf"));
    }
}
