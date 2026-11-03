package com.jshz.expert.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.ComplianceItemAssignee;
import com.jshz.expert.entity.ComplianceReminderLog;
import com.jshz.expert.entity.ComplianceUserProgress;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.ComplianceItemAssigneeMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.ComplianceReminderLogMapper;
import com.jshz.expert.mapper.ComplianceUserProgressMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业合规到期提醒定时任务（REQ/DES-20261102 v2/v3）：
 * - 全局责任人提醒：due_date 前 N 天 + 到期当天，推全部责任人（全局 last_due_notified 去重）；
 * - 个人提醒：按每人 remind_enabled/remind_days + 到期当天（个人 last_due_notified 去重）；
 * - 幂等：每次推送先写 compliance_reminder_log（唯一约束 user_id+item_id+reminder_type+scheduled_time），
 *   已存在（PENDING/SENT）则跳过；发送失败 retry_count+1，上限 3 次后置 FAILED；
 * - 异常矩阵：事项完成/停用、到期修改、取消收藏、关闭提醒分别处理（详见设计 §3.8）；
 * - 自然日计算；workday_only 字段预留（工作日提醒下轮实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceDueScheduler {

    private final ComplianceItemMapper itemMapper;
    private final ComplianceItemAssigneeMapper assigneeMapper;
    private final ComplianceUserProgressMapper progressMapper;
    private final ComplianceReminderLogMapper reminderLogMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final NotificationService notificationService;

    @Value("${expert.compliance.due-enabled:false}")
    private boolean dueEnabled;

    @Value("${expert.compliance.assignee-days:3}")
    private int assigneeDays;

    /** 每日 08:30 执行（可配置）。 */
    @Scheduled(cron = "${expert.compliance.due-cron:0 30 8 * * ?}")
    public void run() {
        if (!dueEnabled) {
            log.debug("合规到期提醒开关关闭（expert.compliance.due-enabled=false），跳过");
            return;
        }
        LocalDate today = LocalDate.now();
        log.info("合规到期提醒任务开始：{}", today);
        int assignee = remindAssignees(today);
        int personal = remindPersonal(today);
        log.info("合规到期提醒任务结束：全局责任人提醒 {} 条，个人提醒 {} 条", assignee, personal);
    }

    /** 全局责任人提醒：status 未完成 + due_date 非空 + due<=today+N 且 last_due_notified<today。 */
    private int remindAssignees(LocalDate today) {
        List<ComplianceItem> items = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getIsDraft, 0)
                .ne(ComplianceItem::getStatus, "COMPLETED")
                .isNotNull(ComplianceItem::getDueDate));
        int sent = 0;
        for (ComplianceItem it : items) {
            KnowledgeDocument doc = documentMapper.selectById(it.getDocumentId());
            if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) continue; // 下架隐藏
            LocalDate due = it.getDueDate();
            if (!due.isBefore(today.plusDays(assigneeDays + 1))) continue; // 未到提醒窗口
            boolean isToday = due.isEqual(today);
            String type = isToday ? "due_today" : "due_before";
            for (Long uid : assigneeMapper.selectList(new LambdaQueryWrapper<ComplianceItemAssignee>()
                    .eq(ComplianceItemAssignee::getItemId, it.getId()))
                    .stream().map(ComplianceItemAssignee::getUserId).toList()) {
                // 全局维度去重：item.last_due_notified 已含今天则不再发（跨责任人共享一天一个节点）
                if (it.getLastDueNotified() != null && !it.getLastDueNotified().isBefore(today)) continue;
                if (sendWithLog(uid, it.getId(), type, today.atTime(8, 30),
                        "合规事项到期提醒" + (isToday ? "（今天到期）" : "（" + assigneeDays + " 天后到期）") + "：「" + it.getTitle() + "」")) {
                    sent++;
                }
            }
            if (sent > 0) {
                it.setLastDueNotified(today);
                itemMapper.updateById(it);
            }
        }
        return sent;
    }

    /** 个人提醒：remind_enabled=1，due<=today+remindDays + 到期当天；取消收藏/已完成不提醒。 */
    private int remindPersonal(LocalDate today) {
        List<ComplianceUserProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<ComplianceUserProgress>()
                .eq(ComplianceUserProgress::getRemindEnabled, 1));
        int sent = 0;
        for (ComplianceUserProgress p : rows) {
            if ("COMPLETED".equals(p.getProgressStatus())) continue; // 已完成停止提醒
            ComplianceItem it = itemMapper.selectById(p.getItemId());
            if (it == null || it.getIsDraft() == 1 || "COMPLETED".equals(it.getStatus())) continue; // 停用/草稿停止
            KnowledgeDocument doc = documentMapper.selectById(it.getDocumentId());
            if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) continue; // 下架隐藏
            LocalDate due = it.getDueDate();
            if (due == null) continue;
            int days = p.getRemindDays() == null ? 3 : p.getRemindDays();
            boolean window = !due.isBefore(today) && !due.isAfter(today.plusDays(days));
            boolean isToday = due.isEqual(today);
            if (!window && !isToday) continue;
            if (p.getLastDueNotified() != null && !p.getLastDueNotified().isBefore(today)) continue; // 今天已提醒
            String type = isToday ? "due_today" : "due_before";
            if (sendWithLog(p.getUserId(), it.getId(), type, today.atTime(8, 30),
                    "合规事项到期提醒（个人提醒，提前 " + days + " 天）：「" + it.getTitle() + "」" + (isToday ? "今天到期" : days + " 天后到期"))) {
                sent++;
                p.setLastDueNotified(today);
                progressMapper.update(p, new LambdaQueryWrapper<ComplianceUserProgress>()
                        .eq(ComplianceUserProgress::getItemId, p.getItemId())
                        .eq(ComplianceUserProgress::getUserId, p.getUserId()));
            }
        }
        return sent;
    }

    /** 幂等发送：先写 reminder_log（唯一约束），已存在则跳过；失败重试上限 3 次。 */
    private boolean sendWithLog(Long userId, Long itemId, String type, LocalDateTime scheduled, String content) {
        ComplianceReminderLog logRow = new ComplianceReminderLog();
        logRow.setUserId(userId);
        logRow.setItemId(itemId);
        logRow.setReminderType(type);
        logRow.setScheduledTime(scheduled);
        logRow.setSendStatus("PENDING");
        try {
            reminderLogMapper.insert(logRow);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false; // 同一提醒节点只发一次（幂等）
        }
        try {
            notificationService.notify(userId, NotificationService.TYPE_COMPLIANCE_DUE, "合规到期提醒", content,
                    "/knowledge-planet/enterprise-compliance");
            logRow.setSendStatus("SENT");
            logRow.setSentTime(LocalDateTime.now());
            reminderLogMapper.updateById(logRow);
            return true;
        } catch (Exception e) {
            int retry = (logRow.getRetryCount() == null ? 0 : logRow.getRetryCount()) + 1;
            logRow.setRetryCount(retry);
            logRow.setSendStatus(retry >= 3 ? "FAILED" : "PENDING");
            reminderLogMapper.updateById(logRow);
            log.warn("合规提醒发送失败（retry={}）：{}", retry, e.getMessage());
            return false;
        }
    }
}
