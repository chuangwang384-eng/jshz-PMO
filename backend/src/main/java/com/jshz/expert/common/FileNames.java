package com.jshz.expert.common;

import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 文件名清洗与扩展名解析的唯一实现（2026-11-13 复用治理收敛）。
 * 历史上 9 处服务各自持有逐字相同的私有实现（safeName / extension / extensionOf），
 * 全部改为引用本类；新增文件存储能力时禁止再复制本地副本。
 */
public final class FileNames {

    private FileNames() {
    }

    /**
     * 文件名清洗：取纯文件名并剥离 ".."、"/"、"\"（防路径穿越的第一道防线）。
     * null 安全：null 或空串返回空串。
     * 循环删除直到稳定：单次 replace 存在重构回归（如 ".../..." 一次扫描后残留 ".."），
     * 循环可保证输出不含任何待剥离片段。
     */
    public static String sanitize(String fileName) {
        String name = new File(fileName == null ? "" : fileName).getName();
        String cleaned = name;
        String previous;
        do {
            previous = cleaned;
            cleaned = previous.replace("..", "").replace("/", "").replace("\\", "");
        } while (!cleaned.equals(previous));
        return cleaned;
    }

    /**
     * 扩展名解析：小写返回；无扩展名返回空串。
     */
    public static String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 存储目录归一化：反斜杠转正斜杠 + 绝对路径 + normalize（各存储服务构造器统一入口）。
     */
    public static Path normalizeDir(String dir) {
        return Paths.get(dir.replace('\\', '/')).toAbsolutePath().normalize();
    }
}
