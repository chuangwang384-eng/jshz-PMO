package com.jshz.expert.service;

import com.jshz.expert.common.PpOcrRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 证件照抠图（Owner 2026-11-11 需求：简历照片自动扣除背景生成透明证件照）。
 * 调用本地 rembg（U²Net 本地推理，数据不出内网），仿 LocalTableOcr 模式：
 * 每个后端进程复用一个 U²-Net 会话。并发时使用原图，工作进程失败或超时后重建。
 *
 * 运行时定位策略（2026-11-11 实测）：rembg 依赖较重，受保护 venv 内安装不稳定，
 * 因此按顺序探测：PHOTOMATTE_PYTHON → E 盘管理 venv → 受保护 venv；
 * 脚本与 u2net 模型按 PHOTOMATTE_ROOT（缺省 Program Files/JSHZ/pp-ocrv5）或 E 盘镜像定位。
 */
@Service
public class PhotoMatteService {
    private static final Logger log = LoggerFactory.getLogger(PhotoMatteService.class);
    private static final Semaphore SLOT = new Semaphore(1);
    private final ExecutorService jobs = Executors.newSingleThreadExecutor(r -> daemon(r, "resume-photo"));
    private final ExecutorService replies = Executors.newSingleThreadExecutor(r -> daemon(r, "resume-photo-reply"));
    private Process worker;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Path workerScript;

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    CompletableFuture<java.util.List<ResumeParseService.PhotoCandidate>> candidates(
            java.util.function.Supplier<java.util.List<ResumeParseService.PhotoCandidate>> task) {
        if (!SLOT.tryAcquire()) return CompletableFuture.completedFuture(java.util.List.of());
        try {
            return CompletableFuture.supplyAsync(task, jobs).whenComplete((result, error) -> SLOT.release());
        } catch (RuntimeException error) {
            SLOT.release();
            throw error;
        }
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmup() {
        candidates(() -> {
            try { ensureWorker(); }
            catch (Exception e) { stopWorker(); log.warn("抠图预热未完成，将在后续请求重试"); }
            return java.util.List.of();
        });
    }

    /** 输入必须为 PNG/JPEG bytes，输出为抠图后的透明 PNG bytes；失败返回 null。 */
    public synchronized byte[] removeBackground(byte[] srcPng) {
        if (srcPng == null || srcPng.length == 0) return null;
        Path input = null;
        Path output = null;
        try {
            ensureWorker();
            input = Files.createTempFile("photomatte-in-", ".png");
            output = Files.createTempFile("photomatte-out-", ".png");
            Files.write(input, srcPng);
            writer.write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    java.util.List.of(input.toString(), output.toString())));
            writer.newLine();
            writer.flush();
            String reply = awaitReply();
            if ("ERROR".equals(reply)) return null;
            if (!"OK".equals(reply)) throw new java.io.IOException("Photo matte worker stopped");
            byte[] result = Files.readAllBytes(output);
            BufferedImage check = ImageIO.read(new java.io.ByteArrayInputStream(result));
            return (check == null || result.length < 1000) ? null : result;
        } catch (InterruptedException e) {
            stopWorker();
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            stopWorker();
            log.warn("证件照抠图不可用，回退原图：{}", e.getClass().getSimpleName());
            return null;
        } finally {
            deleteTemporary(input);
            deleteTemporary(output);
        }
    }

    private synchronized void ensureWorker() throws Exception {
        if (worker != null && worker.isAlive()) return;
        Path python = resolvePython();
        Path script = resolveScript();
        if (python == null || script == null) throw new java.io.IOException("Photo matte runtime unavailable");
        stopWorker();
        try (var resource = getClass().getResourceAsStream("/photomatte-worker.py")) {
            workerScript = Files.createTempFile("photomatte-worker-", ".py");
            Files.write(workerScript, java.util.Objects.requireNonNull(resource).readAllBytes());
        }
        var builder = new ProcessBuilder(python.toString(), "-I", "-u", "-X", "utf8", workerScript.toString())
                .directory(script.getParent().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        worker = builder.start();
        reader = new BufferedReader(new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(worker.getOutputStream(), StandardCharsets.UTF_8));
        if (!"READY".equals(awaitReply())) throw new java.io.IOException("Photo matte worker did not become ready");
    }

    private String awaitReply() throws Exception {
        var response = replies.submit(reader::readLine);
        try { return response.get(15, TimeUnit.SECONDS); }
        finally { response.cancel(true); }
    }

    private synchronized void stopWorker() {
        if (worker != null) worker.destroyForcibly();
        worker = null;
        deleteTemporary(workerScript);
        workerScript = null;
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        jobs.shutdownNow();
        stopWorker();
        replies.shutdownNow();
    }

    /** 受保护 venv 根目录：PHOTOMATTE_ROOT 可覆盖，缺省 ProgramFiles/JSHZ/pp-ocrv5。 */
    private static String resolveRoot() {
        String programFiles = System.getenv().getOrDefault("ProgramFiles", "C:/Program Files");
        return System.getenv().getOrDefault("PHOTOMATTE_ROOT", programFiles + "/JSHZ/pp-ocrv5");
    }

    /**
     * 历史管理 venv 根目录（E 盘实测可用镜像）：默认保留原路径以维持运行时行为不变，
     * 可通过 PHOTOMATTE_LEGACY_ROOT 覆盖或置空跳过（交接记录：E 盘 venv 安装不稳定，正式出口为 PHOTOMATTE_PYTHON/PHOTOMATTE_ROOT）。
     */
    private static String legacyRoot() {
        return System.getenv().getOrDefault("PHOTOMATTE_LEGACY_ROOT", "E:/AI助手开发/pp-ocrv5");
    }

    /** Python 解释器探测：显式配置 → E 盘管理 venv（实测可用）→ 受保护 venv。 */
    private Path resolvePython() {
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        String explicit = System.getenv("PHOTOMATTE_PYTHON");
        if (explicit != null && !explicit.isBlank()) candidates.add(Path.of(explicit));
        String legacy = legacyRoot();
        if (legacy != null && !legacy.isBlank()) candidates.add(PpOcrRuntime.pythonUnder(Path.of(legacy)));
        candidates.add(PpOcrRuntime.pythonUnder(Path.of(resolveRoot())));
        return PpOcrRuntime.firstExisting(candidates);
    }

    /** 脚本探测：受保护目录 → E 盘镜像。 */
    private Path resolveScript() {
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        candidates.add(PpOcrRuntime.scriptUnder(Path.of(resolveRoot()), "photomatte.py"));
        String legacy = legacyRoot();
        if (legacy != null && !legacy.isBlank()) candidates.add(PpOcrRuntime.scriptUnder(Path.of(legacy), "photomatte.py"));
        return PpOcrRuntime.firstExisting(candidates);
    }

    private static void deleteTemporary(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (java.io.IOException e) {
            log.debug("抠图临时文件清理失败", e);
        }
    }
}
