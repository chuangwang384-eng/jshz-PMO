package com.jshz.expert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 定时任务与异步执行配置（本项目首次引入 @Scheduled / @Async）。
 *
 * <p>wecomSyncExecutor：企微同步专用单线程异步执行器——手动/定时同步串行排队，
 * 避免并发同步互相覆盖；拒绝策略为 CallerRuns 使并发触发方感知同步进行中。</p>
 */
@Configuration
@EnableScheduling
@EnableAsync
public class WecomAsyncConfig {

    @Bean("wecomSyncExecutor")
    public Executor wecomSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("wecom-sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
