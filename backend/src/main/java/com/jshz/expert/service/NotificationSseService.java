package com.jshz.expert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 消息通知 SSE 推送（需求 E，2026-11-25）：登录用户经 GET /api/notifications/sse 建立长连接，
 * 通知埋点（{@code notify}）时向该用户全部在线连接推「refresh」信号；前端收到信号后拉取未读数与列表。
 *
 * <p>设计边界：</p>
 * <ul>
 *     <li>SSE 只做「有新消息」信号，不携带业务数据——通知写入与推送不在同一事务，
 *     推送先于事务提交到达属可接受（前端收到信号后自行拉取，拉不到就等下轮）。</li>
 *     <li>30 秒轮询保留为兜底（连接中断自动重连期间由轮询补位），因此推送丢失不影响正确性。</li>
 *     <li>心跳 25 秒一次（低于 Nginx proxy_read_timeout 60s），并依赖响应头 X-Accel-Buffering: no 关闭代理缓冲。</li>
 * </ul>
 */
@Slf4j
@Service
public class NotificationSseService {

    /** 单用户连接数上限：超出时挤掉最旧连接，防止异常客户端累积。 */
    private static final int MAX_EMITTERS_PER_USER = 3;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 注册本人连接（Controller 已完成登录校验）。 */
    public SseEmitter register(long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        while (list.size() > MAX_EMITTERS_PER_USER) {
            SseEmitter oldest = list.remove(0);
            oldest.complete();
        }
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        // 立即发一条注释行，促使代理尽早冲刷响应头
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            list.remove(emitter);
        }
        return emitter;
    }

    /** 通知埋点后调用：向该用户全部在线连接推刷新信号；无在线连接时静默（轮询兜底）。 */
    public void pushRefresh(long userId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("refresh").data(String.valueOf(System.currentTimeMillis())));
            } catch (Exception e) {
                list.remove(emitter);
                emitter.complete();
            }
        }
    }

    /** 心跳：低于 Nginx 60s 读超时；失败连接由 send 异常路径清理。 */
    @Scheduled(cron = "0/25 * * * * ?")
    public void heartbeat() {
        for (CopyOnWriteArrayList<SseEmitter> list : emitters.values()) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    list.remove(emitter);
                    emitter.complete();
                }
            }
        }
    }
}
