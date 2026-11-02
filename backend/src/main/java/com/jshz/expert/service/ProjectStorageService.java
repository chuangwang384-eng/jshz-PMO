package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 项目资料存储服务：文件落盘 项目资料库（expert.project.storage-dir）。
 * 上传文件重命名为随机名（project_<uuid>.<ext>）防穿越；读取按安全名校验。
 * 富文本内容以 JSON 文件形式存于 _richtext 子目录，同样防穿越。
 */
@Service
public class ProjectStorageService {

    private final Path storageDir;

    public ProjectStorageService(@Value("${expert.project.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir.replace('\\', '/')).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureDir() {
        try {
            Files.createDirectories(storageDir);
            Files.createDirectories(storageDir.resolve("_richtext"));
        } catch (IOException e) {
            throw new IllegalStateException("项目资料库目录创建失败: " + storageDir, e);
        }
    }

    /**
     * 保存上传文件（无类型/大小限制，≤50MB 由 servlet 兜底），返回随机文件名。
     */
    public String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "bin" : original.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.isEmpty()) {
            ext = "bin";
        }
        String fileName = "project_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.copy(file.getInputStream(), storageDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
        return fileName;
    }

    /**
     * 保存富文本内容（JSON 文件），返回相对路径（_richtext/proj{projectId}_p{phaseId}_t{templateId}.json）。
     */
    public String saveRichText(Long projectId, Long phaseId, Long templateId, String content) {
        String fileName = "proj" + projectId + "_p" + phaseId + "_t" + templateId + ".json";
        Path p = storageDir.resolve("_richtext").resolve(fileName).normalize();
        try {
            Files.writeString(p, content);
        } catch (IOException e) {
            throw new BusinessException("富文本保存失败: " + e.getMessage());
        }
        return "_richtext/" + fileName;
    }

    /**
     * 读取富文本内容（相对路径解析，防穿越），不存在返回 null。
     */
    public String readRichText(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        String safe = Paths.get(filePath).getFileName().toString();
        Path p = storageDir.resolve("_richtext").resolve(safe).normalize();
        if (!p.startsWith(storageDir) || !Files.exists(p) || !Files.isRegularFile(p)) {
            return null;
        }
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 按相对路径解析真实文件（防路径穿越），不存在返回 null。
     */
    public Path resolveFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        String safeName = Paths.get(filePath).getFileName().toString();
        Path p = storageDir.resolve(safeName).normalize();
        if (!p.startsWith(storageDir) || !Files.exists(p) || !Files.isRegularFile(p)) {
            return null;
        }
        return p;
    }

    /**
     * 删除文件（含富文本 JSON；失败静默，不影响主流程）。
     */
    public void deleteFile(String filePath) {
        try {
            Path p = resolveFile(filePath);
            if (p != null) {
                Files.deleteIfExists(p);
            }
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
    }
}
