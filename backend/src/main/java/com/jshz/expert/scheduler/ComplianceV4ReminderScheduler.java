package com.jshz.expert.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.entity.ComplianceExecution;
import com.jshz.expert.entity.ComplianceExecutionReminder;
import com.jshz.expert.entity.ComplianceExecutionTask;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.ComplianceExecutionMapper;
import com.jshz.expert.mapper.ComplianceExecutionReminderMapper;
import com.jshz.expert.mapper.ComplianceExecutionTaskMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业合规 v4 实例级提醒调度器（REQ/DES-COMPLIANCE-V4 §6）：
 * - 扫描 PENDING 且到期的提醒节点；发送前再次校验：清单 ACTIVE、任务适用未完成、
 *   schedule_revision 与任务当前一致（改期/完成/清除即失效旧节点）、来源文档可用；
 * - 唯一约束 + revision 保证同节点只发一次；失败有界重试（≤3）后置 FAILED；
 * - 站内消息正文最小化（不含备注/敏感条款），只带任务链接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceV4ReminderScheduler {

    private final ComplianceExecutionReminderMapper reminderMapper;
    private final ComplianceExecutionTaskMapper taskMapper;
    private final ComplianceExecutionMapper executionMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final NotificationService notificationService;

    @Value("${expert.compliance.v4-reminder-enabled:true}")
    private boolean enabled;

    /** 每分钟扫描到期提醒（量大时可调 cron）。 */
    @Scheduled(cron = "${expert.compliance.v4-reminder-cron:0 * * * * ?}")
    public void run() {
        if (!enabled) return;
        List<ComplianceExecutionReminder> due = reminderMapper.selectList(new LambdaQueryWrapper<ComplianceExecutionReminder>()
                .eq(ComplianceExecutionReminder::getState, "PENDING")
                .le(ComplianceExecutionReminder::getScheduledAt, LocalDateTime.now()));
        int sent = 0;
        int failed = 0;
        for (ComplianceExecutionReminder r : due) {
            try {
                if (sendOne(r)) sent++;
                else failed++;
            } catch (Exception e) {
                failed++;
                log.warn("v4 提醒发送异常（reminderId={}）：{}", r.getId(), e.getMessage());
            }
        }
        if (sent > 0 || failed > 0) log.info("v4 提醒调度：发送 {}，失败/跳过 {}", sent, failed);
    }

    private boolean sendOne(ComplianceExecutionReminder r) {
        // 发送前二次校验 revision（改期/完成/清除已使旧节点失效）
        ComplianceExecutionTask task = taskMapper.selectById(r.getTaskId());
        if (task == null || !"APPLICABLE".equals(task.getApplicability())
                || "COMPLETED".equals(task.getProgress())
                || task.getScheduleRevision() == null
                || !task.getScheduleRevision().equals(r.getScheduleRevision())) {
            mark(r, "CANCELLED", null);
            return false;
        }
        ComplianceExecution e = executionMapper.selectById(task.getExecutionId());
        if (e == null || !"ACTIVE".equals(e.getState())) {
            mark(r, "CANCELLED", null);
            return false;
        }
        KnowledgeDocument doc = documentMapper.selectById(e.getSourceDocumentId());
        if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
            mark(r, "CANCELLED", null);
            return false;
        }
        try {
            r.setState("SENDING");
            reminderMapper.updateById(r);
            String label = "due_before".equals(r.getKind())
                    ? "合规任务即将到期（提前提醒）" : "due_today".equals(r.getKind())
                    ? "合规任务今天到期" : "合规任务等待跟进复查";
            notificationService.notify(r.getRecipientUserId(),
                    "compliance_due", label,
                    "任务「" + task.getSourceNodeCode() + "」：" + label + "，请到企业合规-我的执行处理",
                    "/knowledge-planet/enterprise-compliance");
            r.setState("SENT");
            r.setSentTime(LocalDateTime.now());
            reminderMapper.updateById(r);
            return true;
        } catch (Exception ex) {
            int retry = (r.getAttemptCount() == null ? 0 : r.getAttemptCount()) + 1;
            r.setAttemptCount(retry);
            r.setState(retry >= 3 ? "FAILED" : "PENDING");
            reminderMapper.updateById(r);
            return false;
        }
    }

    private void mark(ComplianceExecutionReminder r, String state, Long nid) {
        r.setState(state);
        if (nid != null) r.setNotificationId(nid);
        reminderMapper.updateById(r);
    }
}
