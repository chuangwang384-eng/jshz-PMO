package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.security.AuthUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProjectAccessRegressionTest {
    @InjectMocks ProjectService service;
    @Mock ProjectMapper projects;
    @Mock ProjectExecutorMapper executors;
    @Mock ProjectAssistantMapper assistants;
    @Mock OperationLogMapper logs;
    @Mock ProjectDocMapper docs;
    @Mock ProjectStorageService storage;
    @Mock OperationLogService audit;
    @Mock ProjectSupplierMapper supplierRelations;
    @Mock SupplierMapper suppliers;
    @Mock ProjectPhaseMapper phases;
    @Mock ProjectPhaseHistoryMapper phaseHistory;
    AuthUser user(long id, String... permissions) { return new AuthUser(id,"fixture",false,false,Set.of(),Set.of(permissions)); }
    Project project() { Project p = new Project(); p.setId(1L); p.setOwnerId(2L); p.setStatus("ACTIVE"); return p; }
    void setup() { ReflectionTestUtils.setField(service,"baseMapper",projects); when(projects.selectById(1L)).thenReturn(project()); }
    @Test void showArchiveLocksProjectBeforeReplacingTheActiveDocument() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"), ProjectDoc.class);
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        Project project = project();
        project.setPhaseId(12L);
        when(projects.selectOne(any())).thenReturn(project);
        byte[] content = {1};
        when(storage.saveGenerated("project_show_", "html", content)).thenReturn("project_show_fixture.html");

        service.archiveShowDocument(1L, 12L, "html", "fixture.html", content, user(2L));

        InOrder order = inOrder(projects, storage, docs);
        order.verify(projects).selectOne(any());
        order.verify(storage).saveGenerated("project_show_", "html", content);
        order.verify(docs).update(isNull(), any());
        ArgumentCaptor<ProjectDoc> inserted = ArgumentCaptor.forClass(ProjectDoc.class);
        order.verify(docs).insert(inserted.capture());
        assertEquals(12L, inserted.getValue().getPhaseId());
        assertEquals("project_show_fixture.html", inserted.getValue().getFilePath());
        assertEquals(0, inserted.getValue().getIsDeleted());
        order.verifyNoMoreInteractions();
    }
    @Test void outsiderRejectedBeforeLogQuery() {
        setup(); when(executors.selectCount(any())).thenReturn(0L);
        BusinessException e = assertThrows(BusinessException.class, () -> service.timeline(1L,user(3L)));
        assertEquals(403,e.getCode()); verifyNoInteractions(logs);
    }
    @Test void ownerExecutorAndViewAllMayRead() {
        setup(); when(logs.selectList(any())).thenReturn(List.of());
        service.timeline(1L,user(2L));
        when(executors.selectCount(any())).thenReturn(1L);
        service.timeline(1L,user(3L)); service.timeline(1L,user(4L,"project:view-all"));
        verify(logs,times(3)).selectList(any());
    }
    @Test void deletingLegacySharedTextKeepsFileUntilLastActiveReference() {
        setup(); ProjectDoc doc = new ProjectDoc(); doc.setId(5L); doc.setProjectId(1L); doc.setFilePath("_richtext/legacy.json");
        when(docs.selectById(5L)).thenReturn(doc); when(docs.selectCount(any())).thenReturn(1L,0L);
        service.deleteDoc(1L,5L,user(2L)); verifyNoInteractions(storage);
        service.deleteDoc(1L,5L,user(2L)); verify(storage).deleteFile("_richtext/legacy.json");
    }
    @Test void legacyMissingSupplierRemainsVisibleAndCanBeRemoved() {
        setup(); ProjectSupplier relation = new ProjectSupplier(); relation.setId(9L);
        relation.setSupplierId(8L); relation.setFee(new java.math.BigDecimal("100"));
        when(supplierRelations.selectList(any())).thenReturn(List.of(relation));
        var rows = service.listSuppliers(1L,user(2L));
        assertEquals(true,rows.get(0).get("missing"));
        assertEquals(new java.math.BigDecimal("100"),rows.get(0).get("fee"));
        when(supplierRelations.selectOne(any())).thenReturn(relation);
        service.removeSupplier(1L,8L,user(2L,"project:manage"));
        verify(supplierRelations).deleteById(9L);
    }

    private Project setupBudgetStage() {
        ReflectionTestUtils.setField(service, "baseMapper", projects);

        Project project = project();
        project.setPhaseId(10L);
        when(projects.selectOne(any())).thenReturn(project);
        when(phases.selectById(10L)).thenReturn(phase(10L, "会议预算制作", 98));
        when(phases.selectById(11L)).thenReturn(phase(11L, "日程上传", 99));
        return project;
    }

    private ProjectPhase phase(long id, String name, int sort) {
        ProjectPhase phase = new ProjectPhase();
        phase.setId(id);
        phase.setName(name);
        phase.setSort(sort);
        return phase;
    }

    @Test void budgetAdvancementRequiresQuoteArchive() {
        setupBudgetStage();
        when(docs.listBudgetQuotePaths(1L, 10L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePhase(1L, 11L, null, user(2L)));

        assertTrue(error.getMessage().contains("保存报价单"));
        verify(projects, never()).updateById(any(Project.class));
    }

    @Test void existingValidQuoteArchiveAllowsAdvancement() {
        Project project = setupBudgetStage();
        project.setCategory("ONLINE");
        when(docs.listBudgetQuotePaths(1L, 10L)).thenReturn(List.of("project_quote_fixture.xlsx"));
        when(storage.resolveFile("project_quote_fixture.xlsx")).thenReturn(java.nio.file.Path.of("fixture.xlsx"));

        service.changePhase(1L, 11L, null, user(2L));

        assertEquals(11L, project.getPhaseId());
        verify(projects).updateById(project);
        verify(phaseHistory).insert(any(ProjectPhaseHistory.class));
    }

    // ==================== 日程上传阶段：专家全匹配推进校验（2026-11-17） ====================

    @Mock ProjectScheduleMapper scheduleMapper;
    @Mock ProjectSpeakerMapper speakerMapper;

    private Project setupScheduleStage() {
        ReflectionTestUtils.setField(service, "baseMapper", projects);

        Project project = project();
        project.setPhaseId(11L);
        when(projects.selectOne(any())).thenReturn(project);
        when(phases.selectById(11L)).thenReturn(phase(11L, "日程上传", 99));
        when(phases.selectById(12L)).thenReturn(phase(12L, "下一阶段", 100));
        return project;
    }

    private ProjectSchedule schedule(long id) {
        ProjectSchedule s = new ProjectSchedule();
        s.setId(id);
        s.setProjectId(1L);
        s.setIsCurrent(1);
        return s;
    }

    @Test void scheduleAdvancementRequiresUploadedSchedule() {
        setupScheduleStage();
        when(scheduleMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePhase(1L, 12L, null, user(2L)));

        assertTrue(error.getMessage().contains("上传日程"));
        verify(projects, never()).updateById(any(Project.class));
    }

    @Test void unmatchedSpeakerBlocksScheduleAdvancement() {
        setupScheduleStage();
        when(scheduleMapper.selectOne(any())).thenReturn(schedule(30L));
        when(speakerMapper.selectCount(any())).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePhase(1L, 12L, null, user(2L)));

        assertTrue(error.getMessage().contains("专家匹配入库"));
        assertTrue(error.getMessage().contains("2"));
        verify(projects, never()).updateById(any(Project.class));
    }

    @Test void fullyMatchedSpeakersAllowScheduleAdvancement() {
        Project project = setupScheduleStage();
        when(scheduleMapper.selectOne(any())).thenReturn(schedule(30L));
        when(speakerMapper.selectCount(any())).thenReturn(0L);

        service.changePhase(1L, 12L, null, user(2L));

        assertEquals(12L, project.getPhaseId());
        verify(projects).updateById(project);
        verify(phaseHistory).insert(any(ProjectPhaseHistory.class));
    }

    @Test void directPaySettlementGateRequiresOnlyApplicableMandatorySlots() {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        Project project = project();
        project.setPhaseId(12L);
        project.setProjectType("DIRECT_PAY");
        when(projects.selectOne(any())).thenReturn(project);
        when(phases.selectById(12L)).thenReturn(phase(12L, "结算资料上传", 102));
        when(phases.selectById(13L)).thenReturn(phase(13L, "会议费用结算及项目结项", 103));
        when(docs.selectList(any())).thenReturn(List.of());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePhase(1L, 13L, null, user(2L)));
        assertTrue(error.getMessage().contains("盖章结算单"));
        assertTrue(error.getMessage().contains("预算单"));
        assertFalse(error.getMessage().contains("平台审核提交凭证"));
        assertFalse(error.getMessage().contains("高铁票据"));
        verify(projects, never()).updateById(any(Project.class));
    }

    @Test void platformSupplementStaysWritableAfterAdvancementForMembers() throws Exception {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        Project project = project();
        project.setPhaseId(13L);
        project.setProjectType("PLATFORM");
        when(projects.selectOne(any())).thenReturn(project);
        when(phases.selectById(12L)).thenReturn(phase(12L, "结算资料上传", 102));
        when(phases.selectById(13L)).thenReturn(phase(13L, "会议费用结算及项目结项", 103));
        var upload = new org.springframework.mock.web.MockMultipartFile("files", "fixture.pdf",
                "application/pdf", new byte[] {1});
        when(storage.saveSettlement(startsWith("project_settlement_U18_"), eq("pdf"), any()))
                .thenReturn("project_settlement_U18_batch_document.pdf");

        assertThrows(BusinessException.class,
                () -> service.saveSettlementMaterials(1L, 12L, "U17", List.of(upload), null, null, user(2L)));
        service.saveSettlementMaterials(1L, 12L, "U18", List.of(upload), null, null, user(2L));

        ArgumentCaptor<ProjectDoc> inserted = ArgumentCaptor.forClass(ProjectDoc.class);
        verify(docs).insert(inserted.capture());
        assertEquals(12L, inserted.getValue().getPhaseId());
        assertEquals("fixture.pdf", inserted.getValue().getFileName());
        verify(docs, never()).update(isNull(), any());
    }

    @Test void outsidersCannotSubmitAndBudgetReferencesCannotCrossProjects() {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        Project project = project();
        project.setPhaseId(12L);
        when(projects.selectOne(any())).thenReturn(project);
        when(executors.selectCount(any())).thenReturn(0L);
        when(phases.selectById(12L)).thenReturn(phase(12L, "结算资料上传", 102));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.saveSettlementMaterials(1L, 12L, "U09", null, null, 99L, user(3L))).getCode());
        assertThrows(BusinessException.class,
                () -> service.saveSettlementMaterials(1L, 12L, "U09", null, null, 99L, user(2L)));
        verify(docs, never()).insert(any(ProjectDoc.class));
    }

    @Test void settlementSlotCountsAndMarkersMatchConfirmedRules() {
        assertEquals(14, ProjectSettlementMaterials.applicable("KEY").size());
        assertEquals(17, ProjectSettlementMaterials.applicable("DIRECT_PAY").size());
        assertEquals(16, ProjectSettlementMaterials.applicable("PLATFORM").size());
        assertEquals(11, ProjectSettlementMaterials.applicable(null).stream()
                .filter(ProjectSettlementMaterials.Slot::required).count());
        ProjectDoc doc = new ProjectDoc();
        doc.setFilePath("project_settlement_U07_batch_na_document.txt");
        assertEquals("batch", ProjectSettlementMaterials.batch(doc));
        assertTrue(ProjectSettlementMaterials.isReason(doc));
    }

    @Test void settlementAppendKeepsPreviouslySubmittedFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        var project = project();
        project.setPhaseId(12L);
        when(projects.selectOne(any())).thenReturn(project);
        when(phases.selectById(12L)).thenReturn(phase(12L, "结算资料上传", 102));
        var existing = new ProjectDoc();
        existing.setId(1L);
        existing.setFilePath("project_settlement_U01_existing_first.pdf");
        when(docs.selectList(any())).thenReturn(List.of(existing));
        when(storage.resolveFile(existing.getFilePath())).thenReturn(java.nio.file.Files.write(temp.resolve("first.pdf"), new byte[]{1}));
        when(storage.saveSettlement(eq("project_settlement_U01_existing_"), eq("pdf"), any()))
                .thenReturn("project_settlement_U01_existing_second.pdf");
        var upload = new org.springframework.mock.web.MockMultipartFile("files", "second.pdf", "application/pdf", new byte[]{2});

        service.saveSettlementMaterials(1L, 12L, "U01", List.of(upload), null, null, true, user(2L));

        var saved = ArgumentCaptor.forClass(ProjectDoc.class);
        verify(docs).insert(saved.capture());
        assertEquals("existing", ProjectSettlementMaterials.batch(saved.getValue()));
        verify(docs, never()).update(isNull(), any());
    }

    @Test void settlementArchiveWritesFixedZipAndPublishesProjectDocument(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        var project = project();
        project.setPhaseId(12L);
        when(projects.selectOne(any())).thenReturn(project);
        when(phases.selectById(12L)).thenReturn(phase(12L, "结算资料上传", 102));
        var files = new ArrayList<ProjectDoc>();
        for (var slot : ProjectSettlementMaterials.applicable(null)) {
            if (!slot.required()) continue;
            var file = new ProjectDoc();
            file.setId((long) files.size() + 1);
            file.setFileName("资料.txt");
            file.setFilePath(slot.prefix() + "current_first.txt");
            files.add(file);
            when(storage.resolveFile(file.getFilePath())).thenReturn(
                    java.nio.file.Files.writeString(temp.resolve(slot.code() + ".txt"), slot.code()));
        }
        var superseded = new ProjectDoc();
        superseded.setId(0L);
        superseded.setFilePath("project_settlement_U01_old_first.txt");
        files.add(0, superseded);
        when(docs.selectList(any())).thenReturn(files);
        var output = new java.io.ByteArrayOutputStream();
        when(storage.saveSettlementPackage(any())).thenAnswer(invocation -> {
            ProjectStorageService.SettlementPackageWriter writer = invocation.getArgument(0);
            writer.write(output);
            return "project_settlement_package_snapshot.zip";
        });

        service.archiveSettlementPackage(1L, 12L, user(2L));

        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(output.toByteArray()))) {
            var first = zip.getNextEntry();
            assertEquals("供应商费用核对/酒店发票/1-资料.txt", first.getName());
            assertEquals("U01", new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            int entries = 1;
            while (zip.getNextEntry() != null) entries++;
            assertEquals(11, entries);
        }
        var saved = ArgumentCaptor.forClass(ProjectDoc.class);
        verify(docs).insert(saved.capture());
        assertEquals("结算资料包.zip", saved.getValue().getFileName());
        assertFalse(ProjectSettlementMaterials.manages(saved.getValue().getFilePath()));
    }

    @Test void settlementDeletionIsScopedToCurrentBatchAndDoesNotPromoteHistory() {
        ReflectionTestUtils.setField(service, "baseMapper", projects);
        var project = project();
        project.setPhaseId(12L);
        when(projects.selectOne(any())).thenReturn(project);
        when(projects.selectById(1L)).thenReturn(project);
        when(phases.selectById(12L)).thenReturn(phase(12L, "结算资料上传", 102));
        var historical = new ProjectDoc();
        historical.setId(1L);
        historical.setFilePath("project_settlement_U08_old_document.pdf");
        var current = new ProjectDoc();
        current.setId(2L);
        current.setFileName("成本单.pdf");
        current.setFilePath("project_settlement_U08_current_document.pdf");
        when(docs.selectList(any())).thenReturn(List.of(historical, current));

        assertThrows(BusinessException.class, () -> service.deleteSettlementFile(1L, 12L, "U08", 1L, user(2L)));
        assertThrows(BusinessException.class, () -> service.deleteSettlementFile(1L, 12L, "U07", 2L, user(2L)));
        service.deleteSettlementFile(1L, 12L, "U08", 2L, user(2L));

        assertEquals(1, current.getIsDeleted());
        assertEquals("fixture", current.getDeletedBy());
        verify(docs).updateById(current);
        var slot = service.settlementOverview(1L, 12L, user(2L)).slots().stream()
                .filter(item -> item.definition().code().equals("U08")).findFirst().orElseThrow();
        assertFalse(slot.complete());
        assertEquals(List.of(List.of(), List.of(historical)), slot.batches());
    }
}
