package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.ComplianceItemAssigneeMapper;
import com.jshz.expert.mapper.ComplianceItemHistoryMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ComplianceMaintenanceTest {
    private final ComplianceItemMapper items = mock(ComplianceItemMapper.class);
    private final ComplianceItemAssigneeMapper assignees = mock(ComplianceItemAssigneeMapper.class);
    private final KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeDocStorageService storage = mock(KnowledgeDocStorageService.class);
    private final ComplianceParseService parser = mock(ComplianceParseService.class);
    private final ComplianceService service = new ComplianceService(null, items, assignees, null,
            mock(ComplianceItemHistoryMapper.class), null, null, documents, null, parser, storage,
            null, null, null, mock(KnowledgeDocVersionMapper.class));

    @TempDir Path directory;

    private Path source() throws Exception {
        Path source = Files.writeString(directory.resolve("fixture.pdf"), "fixture");
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("测试合规文档");
        doc.setCategory("enterprise-compliance");
        doc.setVersion(1);
        doc.setFileName("fixture.pdf");
        doc.setFilePath("fixture.pdf");
        doc.setFileType("pdf");
        when(documents.selectOne(any())).thenReturn(doc);
        when(storage.resolveDocument("fixture.pdf")).thenReturn(source);
        return source;
    }

    @Test
    void emptyParseKeepsCurrentItems() throws Exception {
        Path file = source();
        when(parser.parse("fixture.pdf", "pdf", file, null))
                .thenReturn(new ComplianceParseService.ParseResult(List.of(), "未解析到候选"));
        assertThrows(BusinessException.class, () -> service.reparse(1L, true, 1L, null));
        verifyNoInteractions(items);
    }

    @Test
    void successfulResetWithdrawsPublishedAndInsertsDraft() throws Exception {
        Path file = source();
        var draft = new ComplianceItem();
        draft.setTitle("重新解析候选");
        var published = new ComplianceItem();
        published.setId(42L);
        published.setIsDraft(0);
        when(parser.parse("fixture.pdf", "pdf", file, null))
                .thenReturn(new ComplianceParseService.ParseResult(List.of(draft), "完成"));
        when(items.selectList(any())).thenReturn(List.of(published), List.of());

        assertEquals(1, service.reparse(1L, true, 1L, null).get("parsed"));
        assertEquals(1, published.getWithdrawn());
        assertEquals(1, draft.getIsDraft());
        assertEquals(1L, draft.getDocumentId());
        verify(items).updateById(published);
        verify(items).insert(draft);
    }

    @Test
    void clearDraftsCountMismatchRejected() throws Exception {
        source();
        when(items.selectList(any())).thenReturn(List.of(new ComplianceItem()));
        assertThrows(BusinessException.class, () -> service.clearDrafts(1L, 2L, null));
        verify(items, never()).deleteById(any(Long.class));
    }

    @Test
    void clearDraftsDeletesAllDrafts() throws Exception {
        source();
        ComplianceItem draft = new ComplianceItem();
        draft.setId(7L);
        draft.setDocumentId(1L);
        draft.setIsDraft(1);
        when(items.selectList(any())).thenReturn(List.of(draft));
        when(items.selectById(7L)).thenReturn(draft);
        when(items.deleteById(7L)).thenReturn(1);

        assertEquals(1, service.clearDrafts(1L, 1L, null).get("deleted"));
        verify(items).deleteById(7L);
        verify(assignees).delete(any());
    }
}
