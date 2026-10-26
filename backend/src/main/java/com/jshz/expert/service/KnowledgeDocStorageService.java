package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * 知识文档存储服务（知识星球）。
 * 允许扩展名：pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png；单文件 ≤50MB。
 * 命名规则：doc_<时间戳>_<原文件名>。
 * 存储目录：D:\jshz-PMO\知识文档库（application.yml expert.knowledge.storage-dir）。
 */
@Service
public class KnowledgeDocStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png");
    private static final long MAX_SIZE = 50L * 1024 * 1024; // 50MB

    private final Path storageDir;

    public KnowledgeDocStorageService(@Value("${expert.knowledge.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir.replace('\\', '/')).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("知识文档目录创建失败: " + this.storageDir, e);
        }
    }

    /**
     * 保存知识文档文件，返回存储文件名。
     */
    public String saveDocument(MultipartFile file) {
        validate(file);
        String originalName = safeName(file.getOriginalFilename());
        String fileName = "doc_" + Instant.now().toEpochMilli() + "_" + originalName;
        try {
            Files.copy(file.getInputStream(), this.storageDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("知识文档保存失败: " + e.getMessage());
        }
        return fileName;
    }

    /**
     * 根据存储文件名解析物理路径（防路径穿越：仅允许当前目录内的文件名）。
     */
    public Path resolveDocument(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("非法的文档文件名");
        }
        Path p = this.storageDir.resolve(safeName(fileName)).normalize();
        if (!p.startsWith(this.storageDir)) {
            throw new BusinessException("非法的文档文件名");
        }
        return p;
    }

    /**
     * 物理删除知识文档文件（永久删除，不可恢复）。
     * 仅允许删除当前目录内文件（复用 resolveDocument 防路径穿越校验）；
     * 文件不存在或删除失败时静默容错，不阻断业务删除流程。
     */
    public void deleteDocument(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        try {
            Path p = resolveDocument(fileName);
            Files.deleteIfExists(p);
        } catch (Exception e) {
            // 物理文件删除失败不阻断记录删除，由运维手工清理
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件大小超过限制（单文件 ≤50MB）");
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 pdf/doc/docx/jpg/jpeg/png 格式文件");
        }
    }

    private String safeName(String fileName) {
        String name = new File(fileName == null ? "" : fileName).getName();
        return name.replace("..", "").replace("/", "").replace("\\", "");
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
