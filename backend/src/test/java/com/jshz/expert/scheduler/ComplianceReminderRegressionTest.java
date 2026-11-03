package com.jshz.expert.scheduler;

import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;

class ComplianceReminderRegressionTest {
    ComplianceItemMapper items = mock(ComplianceItemMapper.class);
    ComplianceItemAssigneeMapper assignees = mock(ComplianceItemAssigneeMapper.class);
    ComplianceUserProgressMapper progresses = mock(ComplianceUserProgressMapper.class);
    ComplianceReminderLogMapper logs = mock(ComplianceReminderLogMapper.class);
    KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
    KnowledgeFavoriteMapper favorites = mock(KnowledgeFavoriteMapper.class);
    NotificationService notifications = mock(NotificationService.class);
    ComplianceDueScheduler scheduler;
    LocalDate due = LocalDate.of(2026, 11, 10);
    ComplianceItem item;
    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "fixture"),
                ComplianceReminderLog.class);
        org.springframework.transaction.PlatformTransactionManager transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        scheduler = new ComplianceDueScheduler(items, assignees, progresses, logs, documents, favorites, notifications, transactions);
        ReflectionTestUtils.setField(scheduler, "assigneeDays", 3);
        item = new ComplianceItem(); item.setId(1L); item.setDocumentId(2L);
        item.setIsDraft(0); item.setStatus("PENDING"); item.setTitle("fixture"); item.setDueDate(due);
        KnowledgeDocument doc = new KnowledgeDocument(); doc.setStatus(1);
        when(items.selectList(any())).thenReturn(List.of(item));
        when(documents.selectById(2L)).thenReturn(doc);
        when(assignees.selectList(any())).thenReturn(List.of());
        when(progresses.selectList(any())).thenReturn(List.of());
        when(favorites.selectList(any())).thenReturn(List.of(favorite()));
        when(logs.insert(any(ComplianceReminderLog.class))).thenAnswer(a -> { ((ComplianceReminderLog)a.getArgument(0)).setId(7L); return 1; });
        when(logs.update(isNull(), any())).thenReturn(1);
    }
    KnowledgeFavorite favorite() { KnowledgeFavorite f = new KnowledgeFavorite(); f.setUserId(5L); f.setDocId(2L); return f; }
    @Test void untouchedFavoriteReceivesDefaultReminder() {
        scheduler.remindOn(due.minusDays(3));
        verify(notifications).notify(eq(5L), anyString(), anyString(), contains("3 天后"), anyString());
    }
    @Test void cancelledFavoriteWithRetainedProgressDoesNotReceiveReminder() {
        ComplianceUserProgress p = new ComplianceUserProgress(); p.setUserId(5L); p.setRemindEnabled(1);
        when(progresses.selectList(any())).thenReturn(List.of(p)); when(favorites.selectList(any())).thenReturn(List.of());
        scheduler.remindOn(due.minusDays(3)); verifyNoInteractions(notifications);
    }
    @Test void onlyBeforeAndDueDatesSend() {
        for (int days = 4; days >= -1; days--) scheduler.remindOn(due.minusDays(days));
        verify(notifications, times(2)).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
    }
    @Test void transientFailureRetriesThenSucceeds() {
        doThrow(new IllegalStateException()).doNothing().when(notifications).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        scheduler.remindOn(due);
        verify(notifications, times(2)).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(logs, atLeastOnce()).updateById(argThat((ComplianceReminderLog l) -> "SENT".equals(l.getSendStatus()) && l.getRetryCount() == 1));
    }
    @Test void persistentFailureStopsAtThreeAndSentDuplicateDoesNotResend() {
        doThrow(new IllegalStateException()).when(notifications).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        scheduler.remindOn(due);
        verify(notifications, times(3)).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(logs, atLeastOnce()).updateById(argThat((ComplianceReminderLog l) -> "FAILED".equals(l.getSendStatus()) && l.getRetryCount() == 3));
    }
    @Test void duplicateSentNodeDoesNotSend() {
        when(logs.insert(any(ComplianceReminderLog.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("fixture"));
        ComplianceReminderLog sent = new ComplianceReminderLog(); sent.setSendStatus("SENT");
        when(logs.selectOne(any())).thenReturn(sent);
        scheduler.remindOn(due); verifyNoInteractions(notifications);
    }
    @Test void completedOrDisabledPersonalProgressStopsBothPaths() {
        ComplianceUserProgress p = new ComplianceUserProgress(); p.setUserId(5L); p.setRemindEnabled(0);
        when(progresses.selectList(any())).thenReturn(List.of(p));
        scheduler.remindOn(due); verifyNoInteractions(notifications);
    }
}
