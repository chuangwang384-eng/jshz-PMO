package com.jshz.expert.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 企微同步异步触发壳。
 *
 * <p>单独成类避免 @Async 自调用失效（Spring 代理不会拦截 this 内部调用）；
 * 统一使用 wecomSyncExecutor（单线程）保证手动/定时同步串行执行。</p>
 *
 * <p>对 {@link ContactSyncService} 使用 {@link Lazy} 注入：ContactSyncService 构造时
 * 又注入本类（用于 start() 触发异步），若双侧 eager 构造会形成循环依赖导致启动失败；
 * runAsync 仅在运行时调用 contactSyncService，懒代理在语义与行为上完全等价。</p>
 */
@Component
public class WecomSyncTrigger {

    private final ContactSyncService contactSyncService;

    public WecomSyncTrigger(@Lazy ContactSyncService contactSyncService) {
        this.contactSyncService = contactSyncService;
    }

    @Async("wecomSyncExecutor")
    public void runAsync(Long logId, boolean dryRun) {
        contactSyncService.execute(logId, dryRun);
    }
}
