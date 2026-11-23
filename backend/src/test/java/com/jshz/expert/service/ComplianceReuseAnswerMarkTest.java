package com.jshz.expert.service;

import com.jshz.expert.entity.ComplianceExecutionTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 需求19 回归：合规执行清单「再次使用」复制答案必须标记待确认，
 * 未核对前不得标记完成；手工填写或显式确认后清除标记（不影响正常填写）。
 */
class ComplianceReuseAnswerMarkTest {

    private ComplianceExecutionTask copied() {
        ComplianceExecutionTask task = new ComplianceExecutionTask();
        task.setAnswer("{\"optionCode\":\"A\"}");
        ComplianceV4Service.markAnswerUnconfirmed(task, 12L);
        return task;
    }

    @Test
    void copiedAnswerIsMarkedPendingConfirmWithSource() {
        ComplianceExecutionTask task = copied();
        assertEquals(1, task.getAnswerUnconfirmed());
        assertTrue(task.getAnswerUnconfirmedSource().contains("清单#12"));
        assertTrue(ComplianceV4Service.answerPendingConfirm(task));
    }

    @Test
    void completionIsBlockedUntilConfirmed() {
        ComplianceExecutionTask task = copied();
        String error = ComplianceV4Service.unconfirmedCompletionError(task, false);
        assertNotNull(error, "未确认的复用答案必须拦截完成");
        assertTrue(error.contains("重新核对"));
        // 同一请求内显式确认 → 放行
        assertNull(ComplianceV4Service.unconfirmedCompletionError(task, true));
    }

    @Test
    void manualAnswerOrConfirmClearsMark() {
        ComplianceExecutionTask task = copied();
        // 手工填写答案（patchTask 内清除并置 0）
        task.setAnswerUnconfirmed(0);
        task.setAnswerUnconfirmedSource(null);
        assertFalse(ComplianceV4Service.answerPendingConfirm(task));
        assertNull(ComplianceV4Service.unconfirmedCompletionError(task, false));
    }

    @Test
    void emptyAnswerNeverRequiresConfirmation() {
        ComplianceExecutionTask task = new ComplianceExecutionTask();
        task.setAnswerUnconfirmed(1);
        task.setAnswer(null);
        assertFalse(ComplianceV4Service.answerPendingConfirm(task));
        task.setAnswer("   ");
        assertFalse(ComplianceV4Service.answerPendingConfirm(task));
        assertNull(ComplianceV4Service.unconfirmedCompletionError(task, false));
    }

    @Test
    void nullTaskIsSafe() {
        assertFalse(ComplianceV4Service.answerPendingConfirm(null));
    }
}
