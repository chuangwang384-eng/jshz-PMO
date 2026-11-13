package com.jshz.expert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

final class LocalTableOcr {
    private static final Logger log = LoggerFactory.getLogger(LocalTableOcr.class);
    private static final Semaphore SLOT = new Semaphore(1);

    private LocalTableOcr() {}

    static String recognize(BufferedImage image) {
        String programFiles = System.getenv().getOrDefault("ProgramFiles", "C:/Program Files");
        String configuredRoot = System.getenv().getOrDefault("COMPLIANCE_PPOCR_ROOT", programFiles + "/JSHZ/pp-ocrv5");
        if (configuredRoot.isBlank() || !SLOT.tryAcquire()) return "";
        Path input = null;
        Path output = null;
        Process process = null;
        try {
            Path root = Path.of(configuredRoot).toAbsolutePath();
            Path python = com.jshz.expert.common.PpOcrRuntime.pythonUnder(root);
            Path script = com.jshz.expert.common.PpOcrRuntime.scriptUnder(root, "compliance-ppocr-table.py");
            if (!com.jshz.expert.common.PpOcrRuntime.ready(python, script)) return "";
            input = Files.createTempFile("compliance-table-", ".png");
            output = Files.createTempFile("compliance-table-", ".tsv");
            ImageIO.write(image, "png", input.toFile());
            process = new ProcessBuilder(python.toString(), "-I", script.toString(), input.toString())
                    .directory(root.toFile())
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(75, TimeUnit.SECONDS) || process.exitValue() != 0) {
                log.warn("本地 PP-OCRv5 表格识别失败或超时，回退原识别引擎");
                return "";
            }
            return Files.readString(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            log.warn("本地 PP-OCRv5 表格识别不可用，回退原识别引擎：{}", e.getClass().getSimpleName());
            return "";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            deleteTemporary(input);
            deleteTemporary(output);
            SLOT.release();
        }
    }

    private static void deleteTemporary(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (java.io.IOException e) {
            log.debug("表格识别临时文件清理失败", e);
        }
    }
}
