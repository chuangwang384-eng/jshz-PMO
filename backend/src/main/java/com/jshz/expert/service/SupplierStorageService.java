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
 * 供应商图片存储服务：文件落盘 供应商图库（expert.supplier.storage-dir）。
 * 上传文件重命名为随机名（supplier_<uuid>.<ext>）防穿越；读取按安全名校验。
 */
@Service
public class SupplierStorageService {

    private final Path storageDir;

    public SupplierStorageService(@Value("${expert.supplier.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir.replace('\\', '/')).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureDir() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("供应商图库目录创建失败: " + storageDir, e);
        }
    }

    /**
     * 保存上传图片（仅 image/*，<=8MB），返回随机文件名。
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("图片文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("仅支持图片文件");
        }
        if (file.getSize() > 8L * 1024 * 1024) {
            throw new BusinessException("图片过大（压缩后应不超过 8MB）");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "jpg" : original.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.isEmpty()) {
            ext = "jpg";
        }
        String fileName = "supplier_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.copy(file.getInputStream(), storageDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("图片保存失败: " + e.getMessage());
        }
        return fileName;
    }

    /**
     * 按文件名解析真实路径（防路径穿越），不存在返回 null。
     */
    public Path resolveImage(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String safeName = Paths.get(fileName).getFileName().toString();
        Path p = storageDir.resolve(safeName).normalize();
        if (!p.startsWith(storageDir) || !Files.exists(p) || !Files.isRegularFile(p)) {
            return null;
        }
        return p;
    }

    /**
     * 删除图片文件（失败静默，不影响主流程）。
     */
    public void deleteImage(String fileName) {
        try {
            Path p = resolveImage(fileName);
            if (p != null) {
                Files.deleteIfExists(p);
            }
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
    }

    /**
     * 按扩展名推断 Content-Type。
     */
    public String contentTypeOf(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        if (n.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }
}
