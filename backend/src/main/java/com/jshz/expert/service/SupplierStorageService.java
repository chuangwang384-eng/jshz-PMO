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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 供应商图片存储服务：文件落盘 供应商图库（expert.supplier.storage-dir）。
 * 上传文件重命名为随机名（supplier_<uuid>.<ext>）防穿越；读取按安全名校验。
 * v2.0「三、图片与主图」：扩展名白名单 + MIME 白名单 + 单张 ≤8MB 校验；
 * 提供目录扫描能力供未关联图片定时清理任务使用。
 */
@Service
public class SupplierStorageService {

    /** 扩展名白名单 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    /** MIME 白名单 */
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");

    /** 单张大小上限 8MB */
    public static final long MAX_SINGLE_SIZE = 8L * 1024 * 1024;

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
     * 保存上传图片：扩展名白名单 + MIME 白名单 + 单张 ≤8MB；返回随机文件名。
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("图片文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 jpg/png/gif/webp/bmp 图片文件");
        }
        if (file.getSize() > MAX_SINGLE_SIZE) {
            throw new BusinessException("图片过大（压缩后应不超过 8MB）");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp/bmp 扩展名的图片文件");
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

    /**
     * 扫描图库目录，返回全部文件名（仅常规文件）。
     */
    public List<String> listAllFileNames() {
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(storageDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException ignored) {
            // 目录不可读时返回空
        }
        return names;
    }

    /**
     * 获取文件创建时间（毫秒时间戳）；无法获取时返回文件修改时间。
     */
    public long creationTimeOf(String fileName) {
        Path p = resolveImage(fileName);
        if (p == null) {
            return 0;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (Exception e) {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (Exception ignored) {
                return 0;
            }
        }
    }
}
