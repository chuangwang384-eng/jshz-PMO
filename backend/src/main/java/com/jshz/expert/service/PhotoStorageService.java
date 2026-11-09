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
 * 照片存储服务（正式版 V1.001，R6/R10）。
 * 三类照片：证件照 photo / 身份证正面 idcard_front / 身份证反面 idcard_back。
 * 命名规则：avatar_<id>_<时间戳>、idcard_front_<id>_<时间戳>、idcard_back_<id>_<时间戳>。
 * 存储目录：D:\jshz-PMO\照片库（application.yml expert.photo.storage-dir）。
 */
@Service
public class PhotoStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final long MAX_SIZE = 5L * 1024 * 1024; // 5MB

    private final Path storageDir;
    private final Path tempDir;

    public PhotoStorageService(@Value("${expert.photo.storage-dir}") String storageDir,
                               @Value("${expert.photo.temp-dir}") String tempDir) {
        this.storageDir = Paths.get(storageDir.replace('\\', '/')).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("照片目录创建失败: " + this.storageDir, e);
        }
        this.tempDir = Paths.get(tempDir.replace('\\', '/')).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.tempDir);
        } catch (IOException e) {
            throw new IllegalStateException("临时照片目录创建失败: " + this.tempDir, e);
        }
    }

    /**
     * 保存照片。
     *
     * @param file     上传文件
     * @param expertId 专家 id（命名规则依赖）
     * @param type     photo / idcard_front / idcard_back
     * @return 存储的文件名
     */
    public String savePhoto(MultipartFile file, Long expertId, String type) {
        validateImage(file);
        if (expertId == null) {
            throw new BusinessException("缺少专家 id，无法按命名规则保存照片");
        }
        String prefix = switch (type) {
            case "idcard_front" -> "idcard_front";
            case "idcard_back" -> "idcard_back";
            default -> "avatar";
        };
        String ext = extension(file.getOriginalFilename());
        String fileName = prefix + "_" + expertId + "_" + Instant.now().toEpochMilli() + "." + ext;
        try {
            Files.copy(file.getInputStream(), this.storageDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("照片保存失败: " + e.getMessage());
        }
        return fileName;
    }

    /**
     * 物理删除照片文件（忽略不存在）。
     */
    public void deletePhoto(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        try {
            Files.deleteIfExists(this.storageDir.resolve(safeName(fileName)));
        } catch (IOException e) {
            // 删除失败不阻断主流程，仅记录
            throw new BusinessException("照片删除失败: " + fileName);
        }
    }

    /**
     * 保存富文本插图：与照片同一存储目录（storageDir），命名 richtext_<时间戳>.<ext>，
     * 通过既有 /api/files/** 静态映射访问，持久保留不随临时目录清理。
     */
    public String saveRichtextImage(MultipartFile file) {
        validateImage(file);
        String ext = extension(file.getOriginalFilename());
        String fileName = "richtext_" + Instant.now().toEpochMilli() + "_" + System.nanoTime() + "." + ext;
        try {
            Files.copy(file.getInputStream(), this.storageDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("富文本插图保存失败: " + e.getMessage());
        }
        return fileName;
    }

    // ==================== 临时照片（需求3：审批流照片预上传） ====================

    /**
     * 保存临时照片：校验规则与正式照片一致，文件落在临时目录（照片库_tmp）。
     */
    public String saveTempPhoto(MultipartFile file) {
        validateImage(file);
        String ext = extension(file.getOriginalFilename());
        String fileName = "tmp_" + Instant.now().toEpochMilli() + "_" + System.nanoTime() + "." + ext;
        try {
            Files.copy(file.getInputStream(), this.tempDir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("临时照片保存失败: " + e.getMessage());
        }
        return fileName;
    }

    /**
     * 临时照片是否存在。
     */
    public boolean existsTempPhoto(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        Path p = this.tempDir.resolve(safeName(fileName)).normalize();
        return p.startsWith(this.tempDir) && Files.exists(p);
    }

    /**
     * 物理删除临时照片（忽略不存在）。
     */
    public void deleteTempPhoto(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return;
        }
        try {
            Files.deleteIfExists(this.tempDir.resolve(safeName(fileName)));
        } catch (IOException e) {
            // 删除失败不阻断主流程
        }
    }

    /**
     * 计算归档目标文件名（只做校验与命名，不做文件拷贝）。
     * 供审批流在事务内把文件名落库，物理归档由 {@link #archiveTempPhoto} 在事务提交后执行，
     * 避免文件 IO 占用数据库事务与连接。
     */
    public String buildArchivedName(String tempName, Long expertId, String type) {
        if (expertId == null) {
            throw new BusinessException("缺少专家 id，无法按命名规则保存照片");
        }
        Path src = this.tempDir.resolve(safeName(tempName)).normalize();
        if (!src.startsWith(this.tempDir) || !Files.exists(src)) {
            throw new BusinessException("临时照片不存在或非法");
        }
        String ext = extension(tempName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/jpeg/png/gif/webp/bmp 格式图片");
        }
        String prefix = switch (type) {
            case "idcard_front" -> "idcard_front";
            case "idcard_back" -> "idcard_back";
            default -> "avatar";
        };
        return prefix + "_" + expertId + "_" + Instant.now().toEpochMilli() + "." + ext;
    }

    /**
     * 物理归档：把临时文件拷贝为指定正式文件名后删除临时文件。
     * 由 {@link #buildArchivedName} 预先算好目标名，事务提交后调用。
     */
    public void archiveTempPhoto(String tempName, String archivedName) {
        if (!StringUtils.hasText(tempName) || !StringUtils.hasText(archivedName)) {
            throw new BusinessException("照片归档参数为空");
        }
        Path src = this.tempDir.resolve(safeName(tempName)).normalize();
        if (!src.startsWith(this.tempDir) || !Files.exists(src)) {
            throw new BusinessException("临时照片不存在或非法");
        }
        Path target = this.storageDir.resolve(safeName(archivedName)).normalize();
        if (!target.startsWith(this.storageDir)) {
            throw new BusinessException("非法的目标照片文件名");
        }
        try {
            Files.copy(src, target);
            Files.deleteIfExists(src);
        } catch (IOException e) {
            throw new BusinessException("临时照片归档失败: " + e.getMessage());
        }
    }

    /**
     * 临时照片归档到正式照片库（命名 + 拷贝一次完成）。
     * 保留给事务外场景使用；审批流内部走 buildArchivedName + archiveTempPhoto 两段式。
     */
    public String moveTempPhoto(String tempName, Long expertId, String type) {
        String fileName = buildArchivedName(tempName, expertId, type);
        archiveTempPhoto(tempName, fileName);
        return fileName;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件大小超过限制（单文件 ≤5MB）");
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/jpeg/png/gif/webp/bmp 格式图片");
        }
    }

    /**
     * 根据文件名解析物理路径（防路径穿越：仅允许当前目录内的文件名）。
     */
    public Path resolvePhoto(String fileName) {
        Path p = this.storageDir.resolve(safeName(fileName)).normalize();
        if (!p.startsWith(this.storageDir)) {
            throw new BusinessException("非法的照片文件名");
        }
        return p;
    }

    private String safeName(String fileName) {
        String name = new File(fileName).getName();
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
