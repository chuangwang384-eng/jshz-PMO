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
 *
 * 2026-11-17 故障修复：服务每次重启后预热 100% 失败（15s 固定超时撑不住 READY 波动 4~34s），
 * 导致每个解析请求重建进程再等 15s 超时、证件照全部回退白底原图（抠图"失效"且解析 +15s）。
 * 修复：超时放宽至 60s、预热失败自动延迟重试、worker 脚本固定文件名（避免随机临时名
 * 反复触发系统实时扫描）、stderr 落盘并在失败日志中带出根因（此前 stderr 被 DISCARD 无法排障）。
 * 权重定位说明：rembg（本机版本）模型路径为 U2NET_HOME/models/&lt;name&gt;/，现网 venv 的
 * models/models/u2net/u2net.onnx 双层结构恰好匹配该规则，E/C 两份权重均可用。
 */
@Service
public class PhotoMatteService {
    private static final Logger log = LoggerFactory.getLogger(PhotoMatteService.class);
    private static final Semaphore SLOT = new Semaphore(1);

    /**
     * 工作进程就绪/单图回复超时（2026-11-17 由固定 15s 放宽）。
     * 实测 READY 冷启动在 4~34s 间波动（首次执行新临时脚本会触发 Defender 深度扫描等系统干扰），
     * 15s 导致每次服务重启后预热 100% 失败、后续每个解析请求重建进程再等 15s 超时回退原图。
     * 60s 仍远小于后端 180s 请求超时，假死兜底语义不变。
     */
    private static final int READY_TIMEOUT_SEC = 60;
    private static final int REPLY_TIMEOUT_SEC = 60;
    /** 预热失败后的自动重试：60s 间隔，最多 3 次（成功即复位）。 */
    private static final int WARMUP_RETRY_MAX = 3;
    private static final long WARMUP_RETRY_DELAY_MS = 60_000L;

    private final ExecutorService jobs = Executors.newSingleThreadExecutor(r -> daemon(r, "resume-photo"));
    private final ExecutorService replies = Executors.newSingleThreadExecutor(r -> daemon(r, "resume-photo-reply"));
    private Process worker;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Path workerScript;
    private Path workerStderr;
    private int warmupRetriesLeft = WARMUP_RETRY_MAX;

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
            try {
                long start = System.currentTimeMillis();
                ensureWorker();
                log.info("抠图工作进程就绪，耗时 {}s", (System.currentTimeMillis() - start) / 1000);
                warmupRetriesLeft = WARMUP_RETRY_MAX;
            } catch (Exception e) {
                stopWorker();
                log.warn("抠图预热未完成（{}），{}s 后自动重试（剩 {} 次），失败详情见 {}",
                        e.getClass().getSimpleName(), WARMUP_RETRY_DELAY_MS / 1000, warmupRetriesLeft, workerStderrTail());
                scheduleWarmupRetry();
            }
            return java.util.List.of();
        });
    }

    /** 预热失败后台延迟重试（守护线程，成功即复位剩余次数；全部用尽则等首个用户请求再试）。 */
    private void scheduleWarmupRetry() {
        if (warmupRetriesLeft <= 0) return;
        warmupRetriesLeft--;
        Thread retry = new Thread(() -> {
            try { Thread.sleep(WARMUP_RETRY_DELAY_MS); } catch (InterruptedException e) { return; }
            warmup();
        }, "resume-photo-warmup-retry");
        retry.setDaemon(true);
        retry.start();
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
            long start = System.currentTimeMillis();
            String reply = awaitReply(REPLY_TIMEOUT_SEC);
            if ("ERROR".equals(reply)) {
                log.warn("证件照抠图失败（worker ERROR），回退原图，详情见 {}", workerStderrTail());
                return null;
            }
            if (!"OK".equals(reply)) throw new java.io.IOException("Photo matte worker stopped");
            byte[] result = Files.readAllBytes(output);
            BufferedImage check = ImageIO.read(new java.io.ByteArrayInputStream(result));
            log.debug("单图抠图耗时 {}ms", System.currentTimeMillis() - start);
            return (check == null || result.length < 1000) ? null : result;
        } catch (InterruptedException e) {
            stopWorker();
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            stopWorker();
            log.warn("证件照抠图不可用，回退原图：{}；worker stderr 尾部：{}", e.getClass().getSimpleName(), workerStderrTail());
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
        // 固定文件名（同内容覆盖写）：随机临时名会让每次重启 worker 都成为"新文件"，
        // 反复触发系统实时扫描，READY 耗时在 4~34s 间波动（2026-11-17 实测）。
        workerScript = Path.of(System.getProperty("java.io.tmpdir"), "photomatte-worker.py");
        try (var resource = getClass().getResourceAsStream("/photomatte-worker.py")) {
            Files.write(workerScript, java.util.Objects.requireNonNull(resource).readAllBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        }
        workerStderr = Path.of(System.getProperty("java.io.tmpdir"), "photomatte-worker.err");
        deleteTemporary(workerStderr);
        var builder = new ProcessBuilder(python.toString(), "-I", "-u", "-X", "utf8", workerScript.toString())
                .directory(script.getParent().toFile())
                .redirectError(ProcessBuilder.Redirect.appendTo(workerStderr.toFile()));
        worker = builder.start();
        reader = new BufferedReader(new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(worker.getOutputStream(), StandardCharsets.UTF_8));
        if (!"READY".equals(awaitReply(READY_TIMEOUT_SEC))) {
            throw new java.io.IOException("Photo matte worker did not become ready");
        }
    }

    private String awaitReply(int timeoutSec) throws Exception {
        var response = replies.submit(reader::readLine);
        try { return response.get(timeoutSec, TimeUnit.SECONDS); }
        finally { response.cancel(true); }
    }

    /** worker stderr 尾部（失败根因定位用；无文件或读失败返回占位文案）。 */
    private String workerStderrTail() {
        if (workerStderr == null || !Files.exists(workerStderr)) return "（无 stderr 记录）";
        try {
            var lines = Files.readAllLines(workerStderr, StandardCharsets.UTF_8);
            var tail = lines.stream().filter(s -> !s.isBlank()).skip(Math.max(0, lines.size() - 6L)).toList();
            return tail.isEmpty() ? "（stderr 为空）" : String.join(" | ", tail);
        } catch (Exception e) {
            return "（stderr 读取失败）";
        }
    }

    private synchronized void stopWorker() {
        if (worker != null) worker.destroyForcibly();
        worker = null;
        // workerScript/workerStderr 保留：脚本固定名复用扫描缓存，stderr 供失败定位
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
