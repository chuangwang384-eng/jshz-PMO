package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceV4EditingRegressionTest {
    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "fixture"), ComplianceExecutionTask.class);
    }
    @InjectMocks ComplianceV4Service service;
    @Mock ComplianceV4AccessService access;
    @Mock ComplianceRolloutService rollout;
    @Mock ComplianceExecutionMapper executions;
    @Mock ComplianceExecutionTaskMapper tasks;
    @Mock ComplianceExecutionEventMapper events;
    @Mock ComplianceExecutionReminderMapper reminders;
    private final AuthUser user = new AuthUser(2L, "fixture", false, false, Set.of(), Set.of());

    private ComplianceExecutionTask editable() {
        ComplianceExecution e = new ComplianceExecution(); e.setId(1L); e.setOwnerUserId(2L); e.setState("ACTIVE");
        ComplianceExecutionTask t = new ComplianceExecutionTask(); t.setId(11L); t.setExecutionId(1L);
        t.setRowVersion(0); t.setScheduleRevision(0); t.setProgress("PROCESSING");
        t.setSnapshot("{\"title\":\"协会\",\"required\":1,\"inputType\":\"MULTI\",\"options\":[{\"optionCode\":\"A\"}]}");
        when(access.canRead(any(), any())).thenReturn(true);
        when(tasks.selectById(11L)).thenReturn(t); when(executions.selectById(1L)).thenReturn(e);
        return t;
    }
    @Test void emptyRequiredMultiCannotComplete() {
        editable();
        assertThrows(BusinessException.class, () -> service.patchTask(11L,
                Map.of("answer", Map.of("optionCodes", List.of()), "progress", "COMPLETED", "rowVersion", 0), user));
        verify(tasks, never()).update(isNull(), any());
    }
    @Test void staleVersionDoesNotWrite() {
        editable();
        assertThrows(BusinessException.class, () -> service.patchTask(11L, Map.of("note", "stale", "rowVersion", 9), user));
        verify(tasks, never()).update(isNull(), any());
    }
    @Test void nullClearsNoteAndDateExplicitly() {
        ComplianceExecutionTask t = editable(); t.setNote("old");
        when(tasks.update(isNull(), any())).thenReturn(1);
        Map<String, Object> body = new HashMap<>(); body.put("note", null); body.put("dueDate", null); body.put("rowVersion", 0);
        service.patchTask(11L, body, user);
        assertNull(t.getNote()); assertNull(t.getDueDate()); assertEquals(1, t.getRowVersion());
        verify(tasks).update(isNull(), any());
    }
    @Test void pageBeyondEndReturnsEmpty() {
        when(executions.selectList(any())).thenReturn(List.of());
        assertEquals(List.of(), service.myTasks(user, "ACTIVE", null, null, null, null, 10, 50).get("items"));
        assertEquals(List.of(), service.listMyExecutions(user, null, null, null, null, 10, 50).get("items"));
    }
    @Test void invalidPaginationRejected() {
        assertThrows(BusinessException.class, () -> service.myTasks(user, "ACTIVE", null, null, null, null, 1, 0));
        assertThrows(BusinessException.class, () -> service.listMyExecutions(user, null, null, null, null, 0, 50));
        assertThrows(BusinessException.class, () -> service.myTasks(user, "OTHER", null, null, null, null, 1, 50));
    }
    @Test void concurrentUpdateConflictIsNotReportedAsSuccess() {
        editable();
        when(tasks.update(isNull(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.patchTask(11L, Map.of("note", "candidate", "rowVersion", 0), user));
    }
    @Test void otherUserCannotEditPrivateTask() {
        editable();
        when(access.canRead(any(), any())).thenReturn(false);
        AuthUser other = new AuthUser(3L, "other", true, false, Set.of(), Set.of("knowledge:maintain"));
        assertThrows(BusinessException.class, () -> service.patchTask(11L, Map.of("note", "denied"), other));
        verify(tasks, never()).update(isNull(), any());
    }
}
