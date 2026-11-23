package com.jshz.expert.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.ComplianceReminderLog;
import com.jshz.expert.entity.ComplianceUserProgress;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.entity.KnowledgeFavorite;
import com.jshz.expert.mapper.ComplianceItemAssigneeMapper;
import com.jshz.expert.mapper.ComplianceItemHistoryMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.ComplianceReminderLogMapper;
import com.jshz.expert.mapper.ComplianceUserProgressMapper;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.KnowledgeFavoriteMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 需求21a/21b 回归：我的待办标注来源状态（下架/删除不复用隐藏），
 * 提醒记录查询改为服务端分页 + 时间范围筛选。
 */
class ComplianceTodoSourceStatusTest {

    private final ComplianceItemMapper items = mock(ComplianceItemMapper.class);
    private final ComplianceItemAssigneeMapper assignees = mock(ComplianceItemAssigneeMapper.class);
    private final ComplianceUserProgressMapper progresses = mock(ComplianceUserProgressMapper.class);
    private final KnowledgeFavoriteMapper favorites = mock(KnowledgeFavoriteMapper.class);
    private final KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
    private final ComplianceReminderLogMapper reminderLogs = mock(ComplianceReminderLogMapper.class);
    private final ComplianceRolloutService rollout = mock(ComplianceRolloutService.class);

    private final ComplianceService service = new ComplianceService(rollout, items, assignees, progresses,
            mock(ComplianceItemHistoryMapper.class), favorites, null, documents, null, null, null,
            null, null, reminderLogs, mock(KnowledgeDocVersionMapper.class));

    private ComplianceItem item(long id, long documentId, String priority, LocalDate due) {
        ComplianceItem it = new ComplianceItem();
        it.setId(id);
        it.setDocumentId(documentId);
        it.setTitle("事项" + id);
        it.setPriority(priority);
        it.setDueDate(due);
        it.setIsDraft(0);
        it.setWithdrawn(0);
        it.setStatus("PENDING");
        return it;
    }

    private KnowledgeDocument doc(long id, int status) {
        KnowledgeDocument d = new KnowledgeDocument();
        d.setId(id);
        d.setTitle("文档" + id);
        d.setStatus(status);
        return d;
    }

    private void stubTodo(List<ComplianceItem> rows, List<KnowledgeDocument> docs) {
        KnowledgeFavorite favorite = new KnowledgeFavorite();
        favorite.setUserId(2L);
        favorite.setDocId(1L);
        when(favorites.selectList(any())).thenReturn(List.of(favorite));
        when(assignees.selectList(any())).thenReturn(List.of());
        when(items.selectList(any())).thenReturn(rows);
        when(documents.selectBatchIds(any())).thenReturn(docs);
        when(progresses.selectOne(any())).thenReturn(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void offShelfAndDeletedSourcesAreShownWithStatus() {
        LocalDate overdue = LocalDate.now().minusDays(3);
        stubTodo(List.of(
                        item(1L, 1L, "HIGH", overdue),
                        item(2L, 2L, "HIGH", overdue),
                        item(3L, 3L, "HIGH", overdue)),
                List.of(doc(1L, 1), doc(2L, 0)));
        Map<String, Object> result = service.myTodo(2L);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("items");
        assertEquals(3, rows.size(), "来源下架/删除的事项仍应展示，不得静默消失");

        Map<String, Object> active = rows.stream().filter(r -> Long.valueOf(1L).equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals("ACTIVE", active.get("sourceStatus"));
        assertEquals(true, active.get("actionable"));
        assertNull(active.get("sourceNotice"));

        Map<String, Object> offline = rows.stream().filter(r -> Long.valueOf(2L).equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals("OFFLINE", offline.get("sourceStatus"));
        assertEquals(false, offline.get("actionable"));
        assertEquals("来源文档已下架，仅供历史参考", offline.get("sourceNotice"));
        assertEquals("文档2", offline.get("documentTitle"));

        Map<String, Object> missing = rows.stream().filter(r -> Long.valueOf(3L).equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals("MISSING", missing.get("sourceStatus"));
        assertEquals(false, missing.get("actionable"));
        assertTrue(String.valueOf(missing.get("sourceNotice")).contains("已删除"));
        assertTrue(String.valueOf(missing.get("documentTitle")).contains("#3"));

        // 失效来源置底，可执行项优先
        assertEquals(1L, rows.get(0).get("id"));
        // 统计只算可执行项，失效来源单独计数
        assertEquals(1, result.get("todoCount"));
        assertEquals(1, result.get("overdueCount"));
        assertEquals(2, result.get("sourceUnavailableCount"));
    }

    @Test
    void progressUpdateIsRejectedWhenSourceIsOffShelf() {
        ComplianceItem it = item(9L, 5L, "MEDIUM", null);
        when(items.selectById(9L)).thenReturn(it);
        when(documents.selectById(5L)).thenReturn(doc(5L, 0));
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateMyProgress(2L, 9L, "PROCESSING", null, null));
        assertTrue(e.getMessage().contains("已下架"));
        verify(progresses, never()).insert(any(ComplianceUserProgress.class));
    }

    @Test
    void reminderLogsArePagedWithTimeRange() {
        Page<ComplianceReminderLog> page = new Page<>(2, 20);
        page.setTotal(57);
        page.setRecords(List.of(new ComplianceReminderLog()));
        when(reminderLogs.selectPage(any(), any())).thenReturn(page);

        Map<String, Object> result = service.reminderLogs(7L, "2026-11-01", "2026-11-30 23:00:00", 2, 20);
        assertEquals(57L, result.get("total"));
        assertEquals(2L, result.get("page"));
        assertEquals(20L, result.get("size"));
        assertEquals(1, ((List<?>) result.get("items")).size());
        assertEquals("2026-11-01", result.get("from"));

        ArgumentCaptor<Page<ComplianceReminderLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(reminderLogs).selectPage(captor.capture(), any());
        assertEquals(2, captor.getValue().getCurrent());
        assertEquals(20, captor.getValue().getSize());
    }

    @Test
    void reminderLogsRejectInvalidPagingAndRange() {
        assertThrows(BusinessException.class, () -> service.reminderLogs(null, null, null, 1, 20));
        assertThrows(BusinessException.class, () -> service.reminderLogs(7L, null, null, 0, 20));
        assertThrows(BusinessException.class, () -> service.reminderLogs(7L, null, null, 1, 0));
        assertThrows(BusinessException.class, () -> service.reminderLogs(7L, null, null, 1,
                ComplianceService.REMINDER_LOG_MAX_PAGE_SIZE + 1));
        assertThrows(BusinessException.class, () -> service.reminderLogs(7L, "2026-12-01", "2026-11-30", 1, 20));
        assertThrows(BusinessException.class, () -> service.reminderLogs(7L, "2026/11/01", null, 1, 20));
        verify(reminderLogs, never()).selectPage(any(), any());
    }

    @Test
    void sameDayRangeIsAccepted() {
        Page<ComplianceReminderLog> page = new Page<>(1, 20);
        page.setRecords(new ArrayList<>());
        when(reminderLogs.selectPage(any(), any())).thenReturn(page);
        assertDoesNotThrow(() -> service.reminderLogs(7L, "2026-11-30", "2026-11-30", 1, 20));
    }
}
