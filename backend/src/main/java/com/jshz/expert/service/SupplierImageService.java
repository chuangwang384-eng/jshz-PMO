package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierImage;
import com.jshz.expert.mapper.SupplierImageMapper;
import com.jshz.expert.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商图片服务（v2.0「三、图片与主图」）：
 * 图片登记于 supplier_image 表；数量 ≤20、单张 ≤8MB（StorageService 校验）、累计 ≤100MB；
 * 首图自动默认主图；设主图同事务先清旧再设新（配合部分唯一索引防并发多主图）；
 * 删图同步物理文件；未关联图片超 30 分钟由定时任务清理。
 */
@Service
@RequiredArgsConstructor
public class SupplierImageService {

    /** 单供应商图片数上限 */
    public static final int MAX_IMAGES = 20;
    /** 单供应商累计大小上限 100MB */
    public static final long MAX_TOTAL_SIZE = 100L * 1024 * 1024;
    /** 自定义命名长度上限 */
    public static final int MAX_NAME_LEN = 50;
    /** 未关联图片保留时长 30 分钟 */
    public static final long ORPHAN_AGE_MS = 30L * 60 * 1000;

    private final SupplierImageMapper imageMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierStorageService storageService;

    /**
     * 上传并登记图片：校验供应商、数量 ≤20、累计 ≤100MB；首图自动设为默认主图。
     * name 为可选自定义命名（≤50 字符，留空=显示原文件名）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SupplierImage upload(Long supplierId, MultipartFile file, String name) {
        if (supplierMapper.selectById(supplierId) == null) {
            throw new BusinessException("供应商不存在");
        }
        Long count = imageMapper.selectCount(new LambdaQueryWrapper<SupplierImage>()
                .eq(SupplierImage::getSupplierId, supplierId));
        if (count != null && count >= MAX_IMAGES) {
            throw new BusinessException("单供应商图片最多 " + MAX_IMAGES + " 张，已达上限");
        }
        Long total = imageMapper.selectList(new LambdaQueryWrapper<SupplierImage>()
                        .eq(SupplierImage::getSupplierId, supplierId).select(SupplierImage::getFileSize))
                .stream().mapToLong(i -> i.getFileSize() == null ? 0L : i.getFileSize()).sum();
        long addSize = file == null ? 0 : file.getSize();
        if (total + addSize > MAX_TOTAL_SIZE) {
            throw new BusinessException("单供应商图片累计不能超过 100MB（当前 " + (total / 1024 / 1024)
                    + "MB，本次 " + (addSize / 1024 / 1024) + "MB）");
        }
        if (StringUtils.hasText(name) && name.trim().length() > MAX_NAME_LEN) {
            throw new BusinessException("图片命名不能超过 " + MAX_NAME_LEN + " 个字符");
        }
        // 自定义命名留空时兜底显示原文件名（超长截断，避免入库失败）
        String displayName = StringUtils.hasText(name) ? name.trim()
                : (file == null || !StringUtils.hasText(file.getOriginalFilename()) ? null
                        : (file.getOriginalFilename().length() > MAX_NAME_LEN
                                ? file.getOriginalFilename().substring(0, MAX_NAME_LEN)
                                : file.getOriginalFilename()));
        String fileName = storageService.saveImage(file);
        try {
            SupplierImage row = new SupplierImage();
            row.setSupplierId(supplierId);
            row.setFileName(fileName);
            row.setName(displayName);
            row.setIsMain(count == null || count == 0 ? 1 : 0);
            row.setFileSize(file.getSize());
            Integer maxSort = imageMapper.selectList(new LambdaQueryWrapper<SupplierImage>()
                            .eq(SupplierImage::getSupplierId, supplierId).select(SupplierImage::getSortOrder))
                    .stream().mapToInt(i -> i.getSortOrder() == null ? 0 : i.getSortOrder()).max().orElse(0);
            row.setSortOrder(maxSort + 1);
            row.setCreateTime(LocalDateTime.now());
            imageMapper.insert(row);
            return row;
        } catch (Exception e) {
            // DB 登记失败时回滚已落盘文件，避免产生孤儿文件
            storageService.deleteImage(fileName);
            throw e;
        }
    }

    /**
     * 设为主图：同一事务内先清该供应商旧主图，再设新主图；
     * 配合部分唯一索引 uk_supplier_main(supplier_id, main_key) 防止并发多主图。
     */
    @Transactional(rollbackFor = Exception.class)
    public SupplierImage setMain(Long supplierId, Long imageId) {
        if (supplierMapper.selectById(supplierId) == null) {
            throw new BusinessException("供应商不存在");
        }
        SupplierImage row = imageMapper.selectOne(new LambdaQueryWrapper<SupplierImage>()
                .eq(SupplierImage::getId, imageId).eq(SupplierImage::getSupplierId, supplierId));
        if (row == null) {
            throw new BusinessException("图片不存在或不属于该供应商");
        }
        imageMapper.update(null, new LambdaUpdateWrapper<SupplierImage>()
                .eq(SupplierImage::getSupplierId, supplierId)
                .eq(SupplierImage::getIsMain, 1)
                .set(SupplierImage::getIsMain, 0));
        imageMapper.update(null, new LambdaUpdateWrapper<SupplierImage>()
                .eq(SupplierImage::getId, imageId)
                .eq(SupplierImage::getSupplierId, supplierId)
                .set(SupplierImage::getIsMain, 1));
        return imageMapper.selectById(imageId);
    }

    /**
     * 删除图片：删除 DB 行并同步删除物理文件；若删除的是主图，自动提升排序最小的一张为新主图。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SupplierImage> delete(Long supplierId, Long imageId) {
        SupplierImage row = imageMapper.selectOne(new LambdaQueryWrapper<SupplierImage>()
                .eq(SupplierImage::getId, imageId).eq(SupplierImage::getSupplierId, supplierId));
        if (row == null) {
            throw new BusinessException("图片不存在或不属于该供应商");
        }
        imageMapper.deleteById(imageId);
        storageService.deleteImage(row.getFileName());
        boolean wasMain = row.getIsMain() != null && row.getIsMain() == 1;
        if (wasMain) {
            SupplierImage next = imageMapper.selectOne(new LambdaQueryWrapper<SupplierImage>()
                    .eq(SupplierImage::getSupplierId, supplierId)
                    .eq(SupplierImage::getIsMain, 0)
                    .orderByAsc(SupplierImage::getSortOrder).orderByAsc(SupplierImage::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                imageMapper.update(null, new LambdaUpdateWrapper<SupplierImage>()
                        .eq(SupplierImage::getId, next.getId())
                        .set(SupplierImage::getIsMain, 1));
            }
        }
        return list(supplierId);
    }

    /**
     * 删除某供应商全部图片：删 DB 行并同步删除物理文件（供应商删除时级联调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(Long supplierId) {
        List<SupplierImage> rows = imageMapper.selectList(new LambdaQueryWrapper<SupplierImage>()
                .eq(SupplierImage::getSupplierId, supplierId));
        if (rows.isEmpty()) {
            return;
        }
        imageMapper.delete(new LambdaQueryWrapper<SupplierImage>()
                .eq(SupplierImage::getSupplierId, supplierId));
        for (SupplierImage row : rows) {
            storageService.deleteImage(row.getFileName());
        }
    }

    /**
     * 图片列表（按 sort_order 升序），供接口与详情展示。
     */
    public List<SupplierImage> list(Long supplierId) {
        return imageMapper.selectList(new LambdaQueryWrapper<SupplierImage>()
                .eq(SupplierImage::getSupplierId, supplierId)
                .orderByAsc(SupplierImage::getSortOrder).orderByAsc(SupplierImage::getId));
    }

    /**
     * 批量查询：supplierId -> 图片列表（供列表/详情 enrich 使用，避免 N+1）。
     */
    public Map<Long, List<SupplierImage>> imageMap(List<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Map.of();
        }
        return imageMapper.selectList(new LambdaQueryWrapper<SupplierImage>()
                        .in(SupplierImage::getSupplierId, supplierIds)
                        .orderByAsc(SupplierImage::getSortOrder).orderByAsc(SupplierImage::getId))
                .stream().collect(Collectors.groupingBy(SupplierImage::getSupplierId,
                        Collectors.toCollection(ArrayList::new)));
    }

    /**
     * 清理未关联图片：扫描图库目录，删除不在 supplier_image 表内、且创建时间超过 30 分钟的文件。
     * 返回本次清理的文件数。
     */
    public int cleanOrphanImages() {
        Set<String> registered = imageMapper.selectList(new LambdaQueryWrapper<SupplierImage>()
                        .select(SupplierImage::getFileName))
                .stream().map(SupplierImage::getFileName).collect(Collectors.toSet());
        if (registered == null) {
            registered = new HashSet<>();
        }
        long deadline = System.currentTimeMillis() - ORPHAN_AGE_MS;
        int cleaned = 0;
        for (String fileName : storageService.listAllFileNames()) {
            if (registered.contains(fileName)) {
                continue;
            }
            long created = storageService.creationTimeOf(fileName);
            if (created > 0 && created <= deadline) {
                storageService.deleteImage(fileName);
                cleaned++;
            }
        }
        return cleaned;
    }
}
