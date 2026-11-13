package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.StorageAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 存储管理与硬删除（Owner 2026-11-12，防存储爆炸）。全部端点要求权限码 storage:manage。
 * 硬删除不可恢复：项目硬删仅限已取消/已删除项目 + 完整项目名确认；孤儿/缓存清理带 expected 集合版本校验。
 */
@RestController
@RequestMapping("/api/storage-admin")
public class StorageAdminController {

    private final StorageAdminService storageAdminService;

    public StorageAdminController(StorageAdminService storageAdminService) {
        this.storageAdminService = storageAdminService;
    }

    /** 存储占用统计与预警（各目录字节/文件数 + 阈值级别 + 磁盘剩余）。 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        return ApiResponse.ok(storageAdminService.stats());
    }

    /** 全部存储目录的孤儿文件清单（scope=project/knowledge/photo/photo-tmp/customer/supplier，缺省 all）。 */
    @GetMapping("/orphans")
    public ApiResponse<Map<String, Object>> orphans(@RequestParam(required = false) String scope,
                                                    @AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return ApiResponse.ok(storageAdminService.allOrphans());
        }
        return ApiResponse.ok(storageAdminService.orphansOf(scope));
    }

    /** 指定目录的孤儿文件硬删除（expectedCount/expectedBytes 集合版本校验，不可恢复）。 */
    @PostMapping("/orphans/purge")
    public ApiResponse<Map<String, Object>> purgeScopedOrphans(@RequestBody Map<String, Object> body,
                                                               @AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        Object scope = body == null ? null : body.get("scope");
        return ApiResponse.ok(storageAdminService.purgeOrphans(
                scope == null ? null : String.valueOf(scope),
                ParamUtils.parseLong(body.get("expectedCount"), null),
                ParamUtils.parseLong(body.get("expectedBytes"), null),
                user));
    }

    /** 兼容旧接口：项目资料库孤儿文件清单（无任何 DB 引用的物理文件）。 */
    @GetMapping("/project-orphans")
    public ApiResponse<Map<String, Object>> projectOrphans(@AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        return ApiResponse.ok(storageAdminService.projectOrphans());
    }

    /** 孤儿文件硬删除（expectedCount/expectedBytes 集合版本校验，不可恢复）。 */
    @PostMapping("/project-orphans/purge")
    public ApiResponse<Map<String, Object>> purgeOrphans(@RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        return ApiResponse.ok(storageAdminService.purgeProjectOrphans(
                ParamUtils.parseLong(body.get("expectedCount"), null),
                ParamUtils.parseLong(body.get("expectedBytes"), null),
                user));
    }

    /** 清空文档预览缓存（可按需再生成；expectedCount 校验）。 */
    @PostMapping("/preview-cache/purge")
    public ApiResponse<Map<String, Object>> clearPreviewCache(@RequestBody Map<String, Object> body,
                                                              @AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        return ApiResponse.ok(storageAdminService.clearPreviewCache(
                ParamUtils.parseLong(body.get("expectedCount"), null), user));
    }

    /** 项目硬删预览：项目名/状态/可删性 + 各类关联行计数 + 物理文件数与字节。 */
    @GetMapping("/projects/{id}/purge-preview")
    public ApiResponse<Map<String, Object>> purgeProjectPreview(@PathVariable Long id,
                                                                @AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        return ApiResponse.ok(storageAdminService.purgeProjectPreview(id));
    }

    /** 硬删除项目全部关联数据（仅 CANCELED/DELETED；confirmName=完整项目名；不可恢复）。 */
    @PostMapping("/projects/{id}/purge")
    public ApiResponse<Map<String, Object>> purgeProject(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal AuthUser user) {
        requireStorageAdmin(user);
        Object name = body == null ? null : body.get("confirmName");
        return ApiResponse.ok(storageAdminService.purgeProject(id,
                name == null ? null : String.valueOf(name), user));
    }

    private void requireStorageAdmin(AuthUser user) {
        if (user == null || !user.hasPermission("storage:manage")) {
            throw new BusinessException(403, "需要「存储管理」权限（storage:manage）");
        }
    }
}
