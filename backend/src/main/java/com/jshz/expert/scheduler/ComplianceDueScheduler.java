package com.jshz.expert.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 自然日精确节点提醒；资格每次重算；唯一日志与条件领取防重复发送。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceDueScheduler {
    private final ComplianceItemMapper itemMapper;
    private final ComplianceItemAssigneeMapper assigneeMapper;
    private final ComplianceUserProgressMapper progressMapper;
    private final ComplianceReminderLogMapper reminderLogMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeFavoriteMapper favoriteMapper;
    private final NotificationService notificationService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Value("${expert.compliance.due-enabled:false}") private boolean dueEnabled;
    @Value("${expert.compliance.assignee-days:3}") private int assigneeDays;

    @Scheduled(cron = "${expert.compliance.due-cron:0 30 8 * * ?}")
    public synchronized void run() {
        if (dueEnabled) remindOn(LocalDate.now());
    }

    /** 固定自然日入口，供隔离回归测试；无系统时钟依赖。 */
    public void remindOn(LocalDate today) {
        List<ComplianceItem> items = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getIsDraft, 0).ne(ComplianceItem::getStatus, "COMPLETED")
                .isNotNull(ComplianceItem::getDueDate));
        for (ComplianceItem item : items) {
            if (!Integer.valueOf(0).equals(item.getIsDraft()) || "COMPLETED".equals(item.getStatus())) continue;
            KnowledgeDocument doc = documentMapper.selectById(item.getDocumentId());
            if (doc == null || !Integer.valueOf(1).equals(doc.getStatus()) || item.getDueDate() == null) continue;
            Map<Long, ComplianceUserProgress> progress = new HashMap<>();
            for (ComplianceUserProgress p : progressMapper.selectList(new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, item.getId()))) progress.put(p.getUserId(), p);
            Set<Long> recipients = new LinkedHashSet<>();
            for (ComplianceItemAssignee a : assigneeMapper.selectList(new LambdaQueryWrapper<ComplianceItemAssignee>()
                    .eq(ComplianceItemAssignee::getItemId, item.getId()))) recipients.add(a.getUserId());
            Set<Long> followers = new HashSet<>();
            for (KnowledgeFavorite f : favoriteMapper.selectList(new LambdaQueryWrapper<KnowledgeFavorite>()
                    .eq(KnowledgeFavorite::getDocId, item.getDocumentId()))) followers.add(f.getUserId());
            recipients.addAll(followers);
            for (Long uid : recipients) {
                ComplianceUserProgress p = progress.get(uid);
                if (p != null && ("COMPLETED".equals(p.getProgressStatus()) || Integer.valueOf(0).equals(p.getRemindEnabled()))) continue;
                // 收藏者使用个人设置；仅责任人使用全局提前天数。同一用户同一节点仅一条。
                int days = followers.contains(uid) ? (p == null || p.getRemindDays() == null ? 3 : p.getRemindDays()) : assigneeDays;
                LocalDate due = item.getDueDate();
                if (!today.equals(due) && !today.equals(due.minusDays(days))) continue;
                String type = today.equals(due) ? "due_today" : "due_before";
                org.springframework.transaction.support.TransactionTemplate transaction =
                        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                for (int transactionAttempt = 1; transactionAttempt <= 3; transactionAttempt++) {
                  try {
                    transaction.executeWithoutResult(status -> sendWithLog(uid, item.getId(), type, today.atTime(8, 30),
                            "合规事项到期提醒：「" + item.getTitle() + "」" + (today.equals(due) ? "今天到期" : days + " 天后到期")));
                    break;
                  } catch (Exception failure) {
                    log.warn("合规提醒节点事务失败，attempt={}，异常类型={}", transactionAttempt, failure.getClass().getSimpleName());
                  }
                }
            }
        }
    }

    private void sendWithLog(Long uid, Long itemId, String type, LocalDateTime scheduled, String content) {
        ComplianceReminderLog row = new ComplianceReminderLog();
        row.setUserId(uid); row.setItemId(itemId); row.setReminderType(type);
        row.setScheduledTime(scheduled); row.setSendStatus("PENDING"); row.setRetryCount(0);
        try {
            reminderLogMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            row = reminderLogMapper.selectOne(new LambdaQueryWrapper<ComplianceReminderLog>()
                    .eq(ComplianceReminderLog::getUserId, uid).eq(ComplianceReminderLog::getItemId, itemId)
                    .eq(ComplianceReminderLog::getReminderType, type).eq(ComplianceReminderLog::getScheduledTime, scheduled));
            if (row == null || !"PENDING".equals(row.getSendStatus())) return;
        }
        int failures = row.getRetryCount() == null ? 0 : row.getRetryCount();
        if (failures >= 3) return;
        // 先条件领取已有PENDING，重复/并发调度不能同时发送。
        if (reminderLogMapper.update(null, new LambdaUpdateWrapper<ComplianceReminderLog>()
                .eq(ComplianceReminderLog::getId, row.getId()).eq(ComplianceReminderLog::getSendStatus, "PENDING")
                .set(ComplianceReminderLog::getSendStatus, "SENDING")) != 1) return;
        while (failures < 3) {
            try {
                notificationService.notify(uid, NotificationService.TYPE_COMPLIANCE_DUE, "合规到期提醒", content,
                        "/knowledge-planet/enterprise-compliance");
            } catch (Exception failure) {
                failures++;
                row.setRetryCount(failures);
                row.setSendStatus(failures >= 3 ? "FAILED" : "SENDING");
                reminderLogMapper.updateById(row);
                log.warn("合规提醒发送失败，attempt={}，异常类型={}", failures, failure.getClass().getSimpleName());
                continue;
            }
            // 通知成功后的日志写失败不进入发送重试，避免同一次运行重复创建通知。
            row.setSendStatus("SENT"); row.setSentTime(LocalDateTime.now());
            reminderLogMapper.updateById(row);
            return;
        }
    }
}
