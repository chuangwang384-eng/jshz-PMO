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

/**
 * 证件照抠图（Owner 2026-11-11 需求：简历照片自动扣除背景生成透明证件照）。
 * 调用本地 rembg（U²Net 本地推理，数据不出内网），仿 LocalTableOcr 模式：
 * 单并发、120 秒超时（含模型冷加载）、失败静默返回 null（调用方回退原图白底 JPG，不阻断解析）。
 *
 * 运行时定位策略（2026-11-11 实测）：rembg 依赖较重，受保护 venv 内安装不稳定，
 * 因此按顺序探测：PHOTOMATTE_PYTHON → E 盘管理 venv → 受保护 venv；
 * 脚本与 u2net 模型按 PHOTOMATTE_ROOT（缺省 Program Files/JSHZ/pp-ocrv5）或 E 盘镜像定位。
 */
@Service
public class PhotoMatteService {
    private static final Logger log = LoggerFactory.getLogger(PhotoMatteService.class);
    private static final Semaphore SLOT = new Semaphore(1);

    /** 输入必须为 PNG/JPEG bytes，输出为抠图后的透明 PNG bytes；失败返回 null。 */
    public byte[] removeBackground(byte[] srcPng) {
        if (srcPng == null || srcPng.length == 0 || !SLOT.tryAcquire()) return null;
        Path input = null;
        Path output = null;
        Process process = null;
        try {
            Path python = resolvePython();
            Path script = resolveScript();
            if (python == null || script == null) {
                log.info("证件照抠图运行时未就绪，回退原图");
                return null;
            }
            input = Files.createTempFile("photomatte-in-", ".png");
            output = Files.createTempFile("photomatte-out-", ".png");
            Files.write(input, srcPng);
            process = new ProcessBuilder(python.toString(), "-I", script.toString(), input.toString(), output.toString())
                    .directory(script.getParent().toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
                log.warn("证件照抠图失败或超时，回退原图");
                return null;
            }
            byte[] result = Files.readAllBytes(output);
            BufferedImage check = ImageIO.read(new java.io.ByteArrayInputStream(result));
            return (check == null || result.length < 1000) ? null : result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("证件照抠图不可用，回退原图：{}", e.getClass().getSimpleName());
            return null;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            deleteTemporary(input);
            deleteTemporary(output);
            SLOT.release();
        }
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
