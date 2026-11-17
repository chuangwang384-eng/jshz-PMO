package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.security.AuthUser;
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
        when(projects.selectById(1L)).thenReturn(project);
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

    @Test void budgetAdvancementRequiresGeneratedQuote() {
        setupBudgetStage();
        when(docs.listBudgetQuotePaths(1L, 10L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePhase(1L, 11L, null, user(2L)));

        assertTrue(error.getMessage().contains("生成报价文件"));
        verify(projects, never()).updateById(any(Project.class));
    }

    @Test void quoteRecordWithoutItsFileCannotCompleteBudgetStage() {
        setupBudgetStage();
        when(docs.listBudgetQuotePaths(1L, 10L)).thenReturn(List.of("project_quote_missing.xlsx"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePhase(1L, 11L, null, user(2L)));

        assertTrue(error.getMessage().contains("生成报价文件"));
        verify(storage).resolveFile("project_quote_missing.xlsx");
    }

    @Test void generatedQuoteAllowsBudgetAdvancementWithoutCustomerConfirmation() {
        Project project = setupBudgetStage();
        project.setCategory("ONLINE");
        when(docs.listBudgetQuotePaths(1L, 10L)).thenReturn(List.of("project_quote_v1.xlsx"));
        when(storage.resolveFile("project_quote_v1.xlsx")).thenReturn(java.nio.file.Path.of("project_quote_v1.xlsx"));

        service.changePhase(1L, 11L, null, user(2L));

        assertEquals(11L, project.getPhaseId());
        verify(projects).updateById(project);
        verify(phaseHistory).insert(any(ProjectPhaseHistory.class));
    }
}
