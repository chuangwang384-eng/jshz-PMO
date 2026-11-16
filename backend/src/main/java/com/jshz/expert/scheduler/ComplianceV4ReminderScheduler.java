package com.jshz.expert.scheduler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.entity.ComplianceExecutionReminder;
import com.jshz.expert.mapper.ComplianceExecutionReminderMapper;
import com.jshz.expert.service.ComplianceV4ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
@Component @RequiredArgsConstructor @Slf4j
public class ComplianceV4ReminderScheduler {
    private final ComplianceExecutionReminderMapper reminders;
    private final ComplianceV4ReminderService delivery;
    @Value("${expert.compliance.v4-reminder-enabled:false}") private boolean enabled;
    @Scheduled(cron="${expert.compliance.v4-reminder-cron:0 * * * * ?}")
    public void run() {
        if (!enabled) return;
        for (ComplianceExecutionReminder r : reminders.selectList(new LambdaQueryWrapper<ComplianceExecutionReminder>()
                .eq(ComplianceExecutionReminder::getState, "PENDING")
                .le(ComplianceExecutionReminder::getScheduledAt, LocalDateTime.now())
                .orderByAsc(ComplianceExecutionReminder::getId).last("LIMIT 200"))) {
            try { delivery.send(r.getId()); }
            catch (RuntimeException failure) { log.warn("V4 reminder node failed, id={}, type={}", r.getId(), failure.getClass().getSimpleName()); }
        }
    }
}
