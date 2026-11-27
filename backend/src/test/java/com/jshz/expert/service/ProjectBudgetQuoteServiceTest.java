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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 报价版本归档路径（2026-11-26 审查整改后）：手动 generate/customer-confirmation/restore 已下线，
 * 仅剩 save(confirm=true) 触发的 archiveConfirmed 归档与 list/download 存档查看。
 */
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
    void archiveConfirmedReturnsFalseOutsideBudgetStageWithoutWriting() {
        var project = new Project();
        project.setId(4L);
        when(projects.requireProject(4L)).thenReturn(project);
        when(projects.phaseNameOf(project)).thenReturn("日程上传");

        assertThat(service().archiveConfirmed(4L, user, mock(ProjectBudgetExportService.class))).isFalse();
        verifyNoInteractions(budgetService, storage, docs, quotes);
    }

    @Test
    void archiveConfirmedRejectsEmptyItemsEvenWithExporter() {
        // P1-①（2026-11-26）：空明细不再凭 exporter 非空放行，封死空报价归档
        var project = new Project();
        project.setId(4L);
        when(projects.requireProject(4L)).thenReturn(project);
        when(projects.phaseNameOf(project)).thenReturn("会议预算制作");
        var budget = new ProjectBudget();
        budget.setId(20L);
        budget.setStatus("CONFIRMED");
        when(budgetService.lockBudget(4L)).thenReturn(budget);
        var item = new ProjectBudgetItem();
        item.setName("场地");
        item.setQuantity(BigDecimal.ZERO);
        when(budgetService.lockedItems(20L)).thenReturn(List.of(item));

        var exporter = mock(ProjectBudgetExportService.class);
        assertThatThrownBy(() -> service().archiveConfirmed(4L, user, exporter))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量大于零");
        verifyNoInteractions(storage, docs);
    }

    @Test
    void confirmingSameBudgetArchivesTheExportOnce() {
        var project = new Project();
        project.setId(4L);
        project.setPhaseId(10L);
        project.setName("会议");
        var budget = new ProjectBudget();
        budget.setId(20L);
        budget.setStatus("CONFIRMED");
        var item = new ProjectBudgetItem();
        item.setName("场地");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(BigDecimal.TEN);
        when(projects.requireProject(4L)).thenReturn(project);
        when(projects.phaseNameOf(project)).thenReturn("会议预算制作");
        when(budgetService.lockBudget(4L)).thenReturn(budget);
        when(budgetService.lockedItems(20L)).thenReturn(List.of(item));
        var exporter = mock(ProjectBudgetExportService.class);
        byte[] excel = {1, 2, 3};
        when(exporter.export(4L, "QUOTE", user)).thenReturn(excel);
        when(storage.saveBudgetQuote(excel)).thenReturn("project_quote_test.xlsx");
        when(docs.insert(any(ProjectDoc.class))).thenAnswer(invocation -> {
            ProjectDoc doc = invocation.getArgument(0);
            assertThat(doc.getPhaseId()).isEqualTo(10L);
            assertThat(doc.getFileName()).isEqualTo("会议报价-V1.xlsx");
            doc.setId(30L);
            return 1;
        });
        var saved = new java.util.concurrent.atomic.AtomicReference<ProjectBudgetQuote>();
        when(quotes.insert(any(ProjectBudgetQuote.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });
        when(quotes.selectOne(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)
                    invocation.getArgument(0);
            query.getSqlSegment();
            var previous = saved.get();
            return previous != null && query.getParamNameValuePairs().containsValue(previous.getRequestKey())
                    ? previous : null;
        });
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), ProjectBudgetQuote.class);

        var quoteService = service();
        assertThat(quoteService.archiveConfirmed(4L, user, exporter)).isTrue();
        String firstKey = saved.get().getRequestKey();
        item.setId(999L);
        quoteService.archiveConfirmed(4L, user, exporter);
        verify(exporter).export(4L, "QUOTE", user);
        assertThat(saved.get().getDocId()).isEqualTo(30L);
        assertThat(saved.get().getRequestKey()).isEqualTo(firstKey);

        item.setUnitPrice(BigDecimal.valueOf(11));
        quoteService.archiveConfirmed(4L, user, exporter);
        verify(exporter, times(2)).export(4L, "QUOTE", user);
        assertThat(saved.get().getRequestKey()).isNotEqualTo(firstKey);
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
