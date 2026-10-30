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
 * 客户附件存储服务（独立客户附件存储目录）。
 * 允许扩展名：pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/zip/rar/7z/txt；单文件 ≤50MB。
 * 命名规则：cust_<时间戳>_<原文件名>。
 * 存储目录：D:\jshz-PMO\客户附件库（application.yml expert.customer.storage-dir）。
 */
@Service
public class CustomerAttachmentStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "zip", "rar", "7z", "txt");
    private static final long MAX_SIZE = 50L * 1024 * 1024; // 50MB

    private final Path storageDir;

    public CustomerAttachmentStorageService(@Value("${expert.customer.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir.replace('\\', '/')).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("客户附件目录创建失败: " + this.storageDir, e);
        }
    }

    /**
     * 保存客户附件文件，返回存储文件名。
     */
    public String saveAttachment(MultipartFile file) {
        validate(file);
        String originalName = safeName(file.getOriginalFilename());
        String fileName = "cust_" + Instant.now().toEpochMilli() + "_" + originalName;
        try {
            Files.copy(file.getInputStream(), this.storageDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("客户附件保存失败: " + e.getMessage());
        }
        return fileName;
    }

    /**
     * 根据存储文件名解析物理路径（防路径穿越：仅允许当前目录内的文件名）。
     */
    public Path resolveAttachment(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("非法的附件文件名");
        }
        Path p = this.storageDir.resolve(safeName(fileName)).normalize();
        if (!p.startsWith(this.storageDir)) {
            throw new BusinessException("非法的附件文件名");
        }
        return p;
    }

    /**
     * 物理删除客户附件文件（删除附件记录时同步清理磁盘文件）。
     * 文件不存在或删除失败时静默容错，不阻断业务删除流程。
     */
    public void deleteAttachment(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        try {
            Path p = resolveAttachment(fileName);
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
            throw new BusinessException("不支持该文件类型，仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/gif/zip/rar/7z/txt");
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
