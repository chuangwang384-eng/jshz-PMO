package com.jshz.expert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectBudgetItem;
import com.jshz.expert.entity.ProjectBudgetQuote;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.mapper.ProjectBudgetMapper;
import com.jshz.expert.mapper.ProjectBudgetQuoteMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectBudgetQuoteServiceTest {
    @Mock ProjectBudgetQuoteMapper quotes;
    @Mock ProjectBudgetMapper budgets;
    @Mock ProjectDocMapper docs;
    @Mock ProjectService projects;
    @Mock ProjectBudgetService budgetService;
    @Mock ProjectStorageService storage;

    private final AuthUser user = new AuthUser(7L, "tester", false, false, Set.of(), Set.of());

    private ProjectBudgetQuoteService service() {
        return new ProjectBudgetQuoteService(quotes, budgets, docs, projects, budgetService, storage,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void generatesExcelAndRegistersItOnBudgetStage() throws Exception {
        var project = new Project();
        project.setId(4L);
        project.setName("会议");
        project.setPhaseId(10L);
        when(projects.requireProject(4L)).thenReturn(project);
        when(projects.phaseNameOf(project)).thenReturn("会议预算制作");
        var budget = new ProjectBudget();
        budget.setId(20L);
        budget.setStatus("CONFIRMED");
        budget.setRemark("内部说明");
        when(budgetService.lockBudget(4L)).thenReturn(budget);
        var item = new ProjectBudgetItem();
        item.setName("场地");
        item.setQuantity(BigDecimal.valueOf(2));
        item.setUnit("场");
        item.setUnitPrice(BigDecimal.valueOf(400));
        item.setAmount(BigDecimal.valueOf(800));
        item.setRemark("内部依据");
        when(budgetService.lockedItems(20L)).thenReturn(List.of(item));
        var workbook = org.mockito.ArgumentCaptor.forClass(byte[].class);
        when(storage.saveBudgetQuote(workbook.capture())).thenReturn("project_quote_test.xlsx");
        when(docs.insert(any(ProjectDoc.class))).thenAnswer(inv -> {
            ProjectDoc doc = inv.getArgument(0);
            assertThat(doc.getPhaseId()).isEqualTo(10L);
            assertThat(doc.getFileName()).isEqualTo("会议报价-V1.xlsx");
            doc.setId(30L);
            return 1;
        });

        var quote = service().generate(4L, new ProjectBudgetQuoteService.Generate(
                UUID.randomUUID().toString(), "对客说明示例"), user);

        assertThat(quote.getDocId()).isEqualTo(30L);
        assertThat(quote.getSnapshot()).contains("内部说明", "内部依据");
        verify(quotes).insert(quote);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook.getValue()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(800);
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).contains("对客说明示例");
            assertThat(sheet.getRow(3).getLastCellNum()).isEqualTo((short) 5);
        }
    }

    @Test
    void retryReturnsExistingVersionWithoutWritingAnotherFile() {
        when(projects.requireProject(4L)).thenReturn(new Project());
        var quote = new ProjectBudgetQuote();
        when(quotes.selectOne(any())).thenReturn(quote);

        assertThat(service().generate(4L, new ProjectBudgetQuoteService.Generate(
                UUID.randomUUID().toString(), null), user)).isSameAs(quote);
        verifyNoInteractions(storage, docs);
    }

    @Test
    void customerConfirmationRejectsEvidenceFromAnotherProject() {
        when(projects.requireProject(4L)).thenReturn(new Project());
        var quote = new ProjectBudgetQuote();
        quote.setDocId(30L);
        when(quotes.selectOne(any())).thenReturn(quote);
        var evidence = new ProjectDoc();
        evidence.setProjectId(5L);
        when(docs.selectById(31L)).thenReturn(evidence);

        assertThatThrownBy(() -> service().confirm(4L, 1L, new ProjectBudgetQuoteService.Confirmation(
                "客户", LocalDateTime.of(2026, 11, 17, 10, 0), null, 31L), user))
                .isInstanceOf(BusinessException.class).hasMessageContaining("本项目");
        verify(quotes, never()).updateById(any(ProjectBudgetQuote.class));
    }

    @Test
    void downloadChecksProjectReadPermissionBeforeLookingUpTheQuote() {
        var project = new Project();
        when(projects.requireProject(4L)).thenReturn(project);
        doThrow(new BusinessException(403, "无权访问")).when(projects).requireProjectReadable(project, user);

        assertThatThrownBy(() -> service().download(4L, 1L, user)).hasMessageContaining("无权访问");
        verifyNoInteractions(quotes, docs, storage);
    }
}
