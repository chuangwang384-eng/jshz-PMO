package com.jshz.expert.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded fan-out; one deadline for the entire request, not one wait per module. */
@Component
@Slf4j
public class DashboardTasks {
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t = new Thread(r, "dashboard-read"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());

    public Map<String,Map<String,Object>> collect(Map<String,Supplier<Map<String,Object>>> jobs, long timeoutMs) {
        long started = System.nanoTime();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.min(timeoutMs, timeoutMs == 1500 ? 1200 : 1500));
        Map<String,Future<Map<String,Object>>> pending = new LinkedHashMap<>();
        jobs.forEach((key, job) -> {
            try { pending.put(key, pool.submit(job::get)); }
            catch (RejectedExecutionException ex) { pending.put(key, CompletableFuture.completedFuture(error("MODULE_BUSY"))); }
        });
        Map<String,Map<String,Object>> result = new LinkedHashMap<>();
        pending.forEach((key, task) -> {
            try {
                Map<String,Object> value = task.isDone() ? task.get() : task.get(Math.max(0, deadline-System.nanoTime()), TimeUnit.NANOSECONDS);
                result.put(key, value);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); task.cancel(true); result.put(key, error("MODULE_UNAVAILABLE"));
            } catch (TimeoutException ex) {
                task.cancel(true); result.put(key, error("MODULE_TIMEOUT"));
            } catch (ExecutionException | CancellationException ex) {
                result.put(key, error("MODULE_UNAVAILABLE"));
            }
        });
        pool.purge();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        result.forEach((module,value) -> {
            if ("error".equals(value.get("status")))
                log.warn("dashboard module={} result={} elapsedMs={}",module,value.get("errorCode"),elapsed);
        });
        return result;
    }

    public static Map<String,Object> error(String code) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("status", "error"); m.put("data", null); m.put("errorCode", code);
        m.put("retryable", true); m.put("message", "暂时无法加载，请重试"); return m;
    }
    @PreDestroy public void close() { pool.shutdownNow(); }
}
