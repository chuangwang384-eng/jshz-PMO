package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;

/** Per-node DB transaction: notification and SENT commit together, rollback on error. */
@Service @RequiredArgsConstructor
public class ComplianceV4ReminderService {
    private final ComplianceExecutionReminderMapper reminders;
    private final ComplianceExecutionMapper executions;
    private final ComplianceExecutionTaskMapper tasks;
    private final ComplianceV4AccessService access;
    private final ComplianceRolloutService rollout;
    private final NotificationService notifications;
    private final PlatformTransactionManager manager;

    public void send(Long id) {
        TransactionTemplate tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try { tx.executeWithoutResult(status -> sendInTransaction(id)); }
        catch (RuntimeException failure) {
            // The failed notification transaction has rolled back. Persist bounded retry
            // in a separate transaction without overwriting another worker's SENT row.
            tx.executeWithoutResult(status -> {
                ComplianceExecutionReminder r = reminders.lockById(id);
                if (r == null || !"PENDING".equals(r.getState())) return;
                int count = r.getAttemptCount() + 1;
                reminders.update(null, new LambdaUpdateWrapper<ComplianceExecutionReminder>()
                    .eq(ComplianceExecutionReminder::getId, id).eq(ComplianceExecutionReminder::getState, "PENDING")
                    .set(ComplianceExecutionReminder::getAttemptCount, count)
                    .set(ComplianceExecutionReminder::getState, count >= 3 ? "FAILED" : "PENDING"));
            });
        }
    }
    private void sendInTransaction(Long id) {
        if (!rollout.isEnabled()) return; // kill switch pauses, never re-enables V3 or loses pending nodes
        ComplianceExecutionReminder first = reminders.selectById(id);
        if (first == null) return;
        try { rollout.lockUser(first.getRecipientUserId()); }
        catch (com.jshz.expert.common.BusinessException inactive) {
            ComplianceExecutionReminder r = reminders.lockById(id);
            if (r != null && "PENDING".equals(r.getState())) { r.setState("CANCELLED"); reminders.updateById(r); }
            return;
        }
        ComplianceExecution e = executions.lockById(first.getExecutionId());
        ComplianceExecutionTask t = tasks.lockById(first.getTaskId());
        ComplianceExecutionReminder r = reminders.lockById(id);
        if (r == null || !"PENDING".equals(r.getState()) || r.getScheduledAt().isAfter(LocalDateTime.now())) return;
        boolean valid = e != null && t != null && e.getId().equals(t.getExecutionId())
            && e.getOwnerUserId().equals(r.getRecipientUserId()) && rollout.isV4(e.getOwnerUserId())
            && "ACTIVE".equals(e.getState()) && "APPLICABLE".equals(t.getApplicability())
            && !"COMPLETED".equals(t.getProgress()) && Integer.valueOf(1).equals(t.getRemindEnabled())
            && t.getScheduleRevision().equals(r.getScheduleRevision()) && access.canRemind(e);
        if (!valid) { r.setState("CANCELLED"); reminders.updateById(r); return; }
        r.setState("SENDING"); reminders.updateById(r);
        String label = switch (r.getKind()) {
            case "due_before" -> "合规任务即将到期";
            case "due_today" -> "合规任务今天到期";
            case "review_due" -> "合规任务需要跟进";
            default -> throw new IllegalArgumentException("Unknown reminder kind");
        };
        notifications.notify(r.getRecipientUserId(), NotificationService.TYPE_COMPLIANCE_DUE, label,
            "请查看本次执行任务", "/knowledge-planet/enterprise-compliance?executionId=" + e.getId() + "&taskId=" + t.getId());
        r.setState("SENT"); r.setSentTime(LocalDateTime.now()); reminders.updateById(r);
    }
}
