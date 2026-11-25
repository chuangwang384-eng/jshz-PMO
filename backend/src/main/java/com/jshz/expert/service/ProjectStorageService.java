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

    /** 对客报价文件名前缀（生成与鉴权/删除保护判定共用；实际落盘全小写，判定统一用大小写敏感 startsWith）。 */
    public static final String QUOTE_FILE_PREFIX = "project_quote_";

    private final Path storageDir;

    public ProjectStorageService(@Value("${expert.project.storage-dir}") String storageDir) {
        this.storageDir = com.jshz.expert.common.FileNames.normalizeDir(storageDir);
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

    public static final String SETTLEMENT_FILE_PREFIX = "project_settlement_";

    /** Store a new settlement attachment and remove it on transaction rollback. */
    String saveSettlement(String prefix, String extension, java.io.InputStream input) {
        String name = prefix + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.copy(input, storageDir.resolve(name));
        } catch (IOException e) {
            deleteFile(name);
            throw new BusinessException("结算资料保存失败");
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteFile(name);
                    }
                });
        return name;
    }

    String saveSettlementPackage(SettlementPackageWriter writer) throws IOException {
        String name = ProjectSettlementMaterials.PACKAGE_PREFIX + UUID.randomUUID().toString().replace("-", "") + ".zip";
        Path path = storageDir.resolve(name);
        try (var output = Files.newOutputStream(path, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            writer.write(output);
        } catch (IOException | RuntimeException e) {
            deleteFile(name);
            throw e;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteFile(name);
                    }
                });
        return name;
    }

    @FunctionalInterface
    interface SettlementPackageWriter {
        void write(java.io.OutputStream output) throws IOException;
    }

    /** 保存不可覆盖的对客报价文件，事务回滚时清理。 */
    public String saveBudgetQuote(byte[] content) {
        return saveGenerated(QUOTE_FILE_PREFIX, "xlsx", content);
    }

    String saveGenerated(String prefix, String extension, byte[] content) {
        String fileName = prefix + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.write(storageDir.resolve(fileName), content, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            deleteFile(fileName);
            throw new BusinessException("项目归档文件保存失败");
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteFile(fileName);
                    }
                });
        return fileName;
    }

    /**
     * 保存富文本内容（JSON 文件），返回包含UUID的独立相对路径，旧文件仍可读取。
     */
    public String saveRichText(Long projectId, Long phaseId, Long templateId, String content) {
        String fileName = "proj" + projectId + "_p" + phaseId + "_t" + templateId + "_" + UUID.randomUUID().toString().replace("-", "") + ".json";
        Path p = storageDir.resolve("_richtext").resolve(fileName).normalize();
        try {
            Files.writeString(p, content);
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) deleteFile("_richtext/" + fileName);
                        }
                    });
            }
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
        if (!filePath.startsWith("_richtext/")) return null;
        Path p = resolveFile(filePath);
        if (p == null) return null;
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
        String normalized = filePath.replace('\\', '/');
        Path relative = Paths.get(normalized);
        if (relative.isAbsolute() || normalized.contains(":") || normalized.contains("..")) return null;
        if (relative.getNameCount() != 1 &&
                !(relative.getNameCount() == 2 && "_richtext".equals(relative.getName(0).toString()))) return null;
        Path p = storageDir.resolve(relative).normalize();
        if (!p.startsWith(storageDir) || !Files.isRegularFile(p)) return null;
        try {
            if (!p.toRealPath().startsWith(storageDir.toRealPath())) return null;
        } catch (IOException e) { return null; }
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
