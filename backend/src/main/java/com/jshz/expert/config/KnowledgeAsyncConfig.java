package com.jshz.expert.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 知识文档异步解析线程池（P2-7）。
 *
 * <p>上传/更新请求只负责流式落盘并提交任务，正文抽取、合规草稿生成在线程池内执行，
 * 避免最大 50MB 文档占用请求线程。队列满时使用 AbortPolicy，由调用方同步标记 FAILED，
 * 不把压力转移回请求线程。</p>
 */
@Configuration
public class KnowledgeAsyncConfig {

    @Bean("knowledgeParseExecutor")
    public Executor knowledgeParseExecutor(
            @Value("${expert.knowledge.parse.core-pool-size:2}") int corePoolSize,
            @Value("${expert.knowledge.parse.max-pool-size:4}") int maxPoolSize,
            @Value("${expert.knowledge.parse.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, corePoolSize), maxPoolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("knowledge-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
