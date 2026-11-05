package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.KnowledgeDocVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.KnowledgeDocStorageService;
import com.jshz.expert.service.KnowledgeDocumentService;
import com.jshz.expert.service.ComplianceService;
import com.jshz.expert.service.KnowledgeFavoriteService;
import com.jshz.expert.service.DownloadAuditService;
import com.jshz.expert.service.DocPreviewService;
import com.jshz.expert.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识星球内容管理接口（知识文档库）。
 * 上传 / 分页列表 / 更新（版本+1保留历史）/ 下架 / 上架 / 永久删除 / 版本历史 / 文件下载预览。
 * 管理类接口由「知识星球维护人」权限码 knowledge:maintain 控制（过滤器 + 业务层双重校验）；
 * 上传 / 更新 / 下架 / 永久删除 / 上架 / 版本历史；收藏 / 预览 / 下载 / 列表浏览保留登录即可。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocStorageService storageService;
    private final KnowledgeFavoriteService favoriteService;
    private final ComplianceService complianceService;
    private final DownloadAuditService downloadAuditService;
    private final DocPreviewService docPreviewService;
    private final OperationLogService operationLogService;

    // ==================== 上传 ====================

    /**
     * 上传文档：POST /api/knowledge/docs?category=&title= + multipart file。仅维护人（knowledge:maintain）。
     */
    @PostMapping("/docs")
    public ApiResponse<KnowledgeDocument> upload(@RequestParam String category,
                                                 @RequestParam String title,
                                                 @RequestParam("file") MultipartFile file,
                                                 @AuthenticationPrincipal AuthUser authUser,
                                                 HttpServletRequest request) {
        requireMaintain(authUser);
        KnowledgeDocument doc = documentService.upload(category, title, file, authUser);
        operationLogService.record(authUser == null ? null : authUser.getUserId(),
                authUser == null ? null : authUser.getUsername(),
                OperationLogService.ACTION_CREATE_DOC, "上传知识文档《" + doc.getTitle() + "》",
                "DOC", doc.getId(), request);
        return ApiResponse.ok(doc);
    }

    // ==================== 分页列表 ====================

    /**
     * 分页列表：GET /api/knowledge/docs?category=&keyword=&fileType=&page=&pageSize=。登录即可。
     * category 可选：不传 = 全部分类；传 = 按分类过滤。
     * fileType 可选：按文件格式等值过滤（image = jpg/jpeg/png 图片集合）。
     * 列表按权限过滤 status：管理员（knowledge:manage）返回上架+下架，普通员工仅上架。
     */
    @GetMapping("/docs")
    public ApiResponse<PageResult<KnowledgeDocument>> list(@RequestParam(required = false) String category,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String fileType,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "20") long pageSize,
                                                           @RequestParam(defaultValue = "false") boolean favoritesOnly,
                                                           @AuthenticationPrincipal AuthUser authUser) {
        Page<KnowledgeDocument> p = documentService.pageList(category, keyword, fileType, page, pageSize, authUser, favoritesOnly);
        return ApiResponse.ok(PageResult.of(p));
    }

    // ==================== 更新 ====================

    /**
     * 更新文档：PUT /api/knowledge/docs/{id}（multipart：title 可选 + file 必填）。
     * 主表 version+1、更新文件字段与 update_by/update_time，版本表追加历史记录。登录即可。
     */
    @PutMapping("/docs/{id}")
    public ApiResponse<KnowledgeDocument> update(@PathVariable Long id,
                                                 @RequestParam(required = false) String title,
                                                 @RequestParam("file") MultipartFile file,
                                                 @AuthenticationPrincipal AuthUser authUser,
                                                 HttpServletRequest request) {
        KnowledgeDocument doc = documentService.updateVersion(id, title, file, authUser);
        operationLogService.record(authUser == null ? null : authUser.getUserId(),
                authUser == null ? null : authUser.getUsername(),
                OperationLogService.ACTION_UPDATE_DOC, "更新知识文档《" + doc.getTitle() + "》（新版本）",
                "DOC", id, request);
        // 企业合规分类：更新成功后重新解析生成新事项草稿（REQ/DES-20261102 FR6；事务外静默，不阻断更新）
        if (complianceService != null) {
            try {
                String fileType = doc == null ? null : doc.getFileType();
                complianceService.parseAndCreateDrafts(id, complianceService.latestVersionIdOf(id),
                        file.getOriginalFilename(), fileType, file.getBytes(), null, authUser);
            } catch (Exception ex) {
                // 静默跳过：解析失败允许维护人手动录入
            }
        }
    return ApiResponse.ok(doc);
    }

    // ==================== 下架 ====================

    /**
     * 下架：DELETE /api/knowledge/docs/{id}（status=0）。需权限码 knowledge:maintain（业务层校验）。
     */
    @DeleteMapping("/docs/{id}")
    public ApiResponse<Void> offShelf(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthUser authUser,
                                      HttpServletRequest request) {
        KnowledgeDocument before = documentService.getById(id);
        documentService.offShelf(id, authUser);
        operationLogService.record(authUser == null ? null : authUser.getUserId(),
                authUser == null ? null : authUser.getUsername(),
                OperationLogService.ACTION_OFF_SHELF_DOC,
                "下架知识文档《" + (before == null ? id : before.getTitle()) + "》",
                "DOC", id, request);
        return ApiResponse.ok();
    }

    /**
     * 永久删除：DELETE /api/knowledge/docs/{id}/permanent。
     * 删除主记录 + 全部版本历史 + 磁盘文件（不可恢复）。需权限码 knowledge:maintain（业务层校验）。
     */
    @DeleteMapping("/docs/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthUser authUser,
                                             HttpServletRequest request) {
        KnowledgeDocument before = documentService.getById(id);
        documentService.permanentDelete(id, authUser);
        operationLogService.record(authUser == null ? null : authUser.getUserId(),
                authUser == null ? null : authUser.getUsername(),
                OperationLogService.ACTION_DELETE_DOC,
                "永久删除知识文档《" + (before == null ? id : before.getTitle()) + "》",
                "DOC", id, request);
        return ApiResponse.ok();
    }

    // ==================== 版本历史 ====================

    /**
     * 版本历史：GET /api/knowledge/docs/{id}/versions。仅维护人（knowledge:maintain）可见。
     */
    @GetMapping("/docs/{id}/versions")
    public ApiResponse<List<KnowledgeDocVersion>> versions(@PathVariable Long id,
                                                           @AuthenticationPrincipal AuthUser authUser) {
        requireMaintain(authUser);
        return ApiResponse.ok(documentService.versions(id));
    }

    // ==================== 上架 ====================

    /**
     * 上架：POST /api/knowledge/docs/{id}/relist（status=1）。
     * 仅维护人（knowledge:maintain）。普通用户立即可见，收藏列表自动恢复；通知全部收藏者。
     */
    @PostMapping("/docs/{id}/relist")
    public ApiResponse<Void> relist(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthUser authUser,
                                    HttpServletRequest request) {
        requireMaintain(authUser);
        KnowledgeDocument before = documentService.getById(id);
        documentService.relist(id, authUser);
        operationLogService.record(authUser == null ? null : authUser.getUserId(),
                authUser == null ? null : authUser.getUsername(),
                OperationLogService.ACTION_RELIST_DOC,
                "上架知识文档《" + (before == null ? id : before.getTitle()) + "》",
                "DOC", id, request);
        return ApiResponse.ok();
    }

    // ==================== 文件下载/预览 ====================

    /**
     * 文件流：GET /api/knowledge/docs/files/{fileName}?download=1&preview=true。登录即可。
     * Content-Disposition 使用 RFC 5987 filename* 支持中文文件名。
     * C4：download=1（附件下载）时写入 download_log 计数（doc_id + type=knowledge_doc）；
     * inline 预览不计入下载次数。
     * preview=true：预览模式。Office 文档（doc/docx/xls/xlsx/ppt/pptx）先经 DocPreviewService
     * 转换为 PDF 缓存后返回 PDF 流（成功即 SUCCESS 语义）；转换失败（含重试后仍失败）返回结构化
     * JSON {code:CONVERT_FAILED, message}；PDF / 图片直接返回原文件流，其他异常返回
     * {code:PREVIEW_FAILED, message}。
     * 未传 preview 时兼容旧调用（维持原 inline 预览行为，Office 转换失败抛业务异常）。
     */
    @GetMapping("/docs/files/{fileName}")
    public ResponseEntity<?> file(@PathVariable String fileName,
                                  @RequestParam(required = false) String download,
                                  @RequestParam(required = false) String preview,
                                  @AuthenticationPrincipal AuthUser authUser,
                                  HttpServletRequest request) throws IOException {
        Path path = storageService.resolveDocument(fileName);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        boolean forceDownload = "1".equals(download) || "true".equalsIgnoreCase(String.valueOf(download));
        boolean previewMode = !forceDownload && ("true".equalsIgnoreCase(preview) || "1".equals(preview));
        if (forceDownload) {
            // 下载留痕（C4）：按存储文件名反查文档，失败不阻断下载
            try {
                KnowledgeDocument doc = documentService.findByFilePath(fileName);
                if (doc != null) {
                    downloadAuditService.recordKnowledgeDoc(authUser, doc.getId(), doc.getTitle(), request);
                }
            } catch (Exception e) {
                // 计数失败不影响下载
            }
        }
        MediaType mediaType = mediaTypeOf(fileName);
        if (previewMode) {
            // 预览模式（?preview=true）：Office 转换失败返回结构化错误码 CONVERT_FAILED，
            // 其他异常返回 PREVIEW_FAILED；成功返回 PDF / 原文件流（SUCCESS 语义）
            try {
                KnowledgeDocument doc = documentService.findByFilePath(fileName);
                if (doc != null && DocPreviewService.isOffice(doc.getFileType())) {
                    Path pdf = docPreviewService.getPreviewFile(
                            doc.getFilePath(), doc.getId(), doc.getVersion(), doc.getFileType());
                    path = pdf;
                    mediaType = MediaType.APPLICATION_PDF;
                }
            } catch (com.jshz.expert.common.BusinessException e) {
                return previewError("CONVERT_FAILED", "文档转换失败，可下载查看");
            } catch (Exception e) {
                return previewError("PREVIEW_FAILED", "预览失败，可下载查看");
            }
        } else if (!forceDownload) {
            // 兼容旧调用（未传 preview 参数）：维持原 inline 预览行为
            try {
                KnowledgeDocument doc = documentService.findByFilePath(fileName);
                if (doc != null && DocPreviewService.isOffice(doc.getFileType())) {
                    Path pdf = docPreviewService.getPreviewFile(
                            doc.getFilePath(), doc.getId(), doc.getVersion(), doc.getFileType());
                    path = pdf;
                    mediaType = MediaType.APPLICATION_PDF;
                }
            } catch (com.jshz.expert.common.BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new com.jshz.expert.common.BusinessException("暂无法在线预览，请下载查看");
            }
        }
        String safeName = path.getFileName().toString();
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (forceDownload ? "attachment" : "inline")
                + "; filename=\"" + safeName.replace("\"", "") + "\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    /** 预览失败结构化响应：{"code":"CONVERT_FAILED|PREVIEW_FAILED","message":"..."}；成功时以文件流返回（SUCCESS 语义）。 */
    private ResponseEntity<?> previewError(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // ==================== 文档收藏（C3） ====================
    // 挂 /favorites 路径：避开 permission 表 DELETE /api/knowledge/docs/**（knowledge:manage）通配规则，
    // 收藏/取消收藏登录即可；userId 一律取当前登录用户，禁止操作他人收藏。

    /**
     * 收藏文档：POST /api/knowledge/favorites/{docId}（登录即可，幂等）。
     */
    @PostMapping("/favorites/{docId}")
    public ApiResponse<Void> favorite(@PathVariable Long docId,
                                      @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        favoriteService.favorite(docId, authUser.getUserId());
        return ApiResponse.ok();
    }

    /**
     * 取消收藏：DELETE /api/knowledge/favorites/{docId}（登录即可，幂等）。
     */
    @DeleteMapping("/favorites/{docId}")
    public ApiResponse<Void> unfavorite(@PathVariable Long docId,
                                        @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        favoriteService.unfavorite(docId, authUser.getUserId());
        return ApiResponse.ok();
    }

    /**
     * 我的收藏列表：GET /api/knowledge/favorites（登录即可）。
     * 返回 [{doc: 文档信息(isFavorite=true,downloadCount 已填充), favoriteTime}]，按收藏时间倒序。
     */
    @GetMapping("/favorites")
    public ApiResponse<List<Map<String, Object>>> myFavorites(@AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        List<Map<String, Object>> favorites = favoriteService.myFavorites(authUser.getUserId());
        documentService.enrichFavorites(favorites);
        return ApiResponse.ok(favorites);
    }

    /**
     * 单文档收藏状态：GET /api/knowledge/favorites/{docId}（登录即可）。
     */
    @GetMapping("/favorites/{docId}")
    public ApiResponse<Map<String, Object>> favoriteStatus(@PathVariable Long docId,
                                                           @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docId", docId);
        data.put("favorited", favoriteService.exists(docId, authUser.getUserId()));
        return ApiResponse.ok(data);
    }

    private void requireUser(AuthUser authUser) {
        if (authUser == null) {
            throw new com.jshz.expert.common.BusinessException("未登录");
        }
    }

    /** 知识星球维护人权限校验（knowledge:maintain；SuperAdmin 由 PermissionService 强制全权）。 */
    private void requireMaintain(AuthUser authUser) {
        requireUser(authUser);
        if (!authUser.hasPermission("knowledge:maintain")) {
            throw new com.jshz.expert.common.BusinessException("无权限执行该操作");
        }
    }

    private MediaType mediaTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
