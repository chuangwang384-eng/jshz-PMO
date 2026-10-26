package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.KnowledgeDocVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.KnowledgeDocStorageService;
import com.jshz.expert.service.KnowledgeDocumentService;
import com.jshz.expert.service.KnowledgeFavoriteService;
import com.jshz.expert.service.DownloadAuditService;
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
 * 上传 / 分页列表 / 更新（版本+1保留历史）/ 下架（knowledge:manage）/ 版本历史 / 文件下载预览。
 * 下架需权限码 knowledge:manage（过滤器 + 业务层双重校验）；其余接口登录即可。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocStorageService storageService;
    private final KnowledgeFavoriteService favoriteService;
    private final DownloadAuditService downloadAuditService;

    // ==================== 上传 ====================

    /**
     * 上传文档：POST /api/knowledge/docs?category=&title= + multipart file。登录即可。
     */
    @PostMapping("/docs")
    public ApiResponse<KnowledgeDocument> upload(@RequestParam String category,
                                                 @RequestParam String title,
                                                 @RequestParam("file") MultipartFile file,
                                                 @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(documentService.upload(category, title, file, authUser));
    }

    // ==================== 分页列表 ====================

    /**
     * 分页列表：GET /api/knowledge/docs?category=&keyword=&page=&pageSize=。登录即可。
     * category 可选：不传 = 全部分类；传 = 按分类过滤。
     * 列表按权限过滤 status：管理员（knowledge:manage）返回上架+下架，普通员工仅上架。
     */
    @GetMapping("/docs")
    public ApiResponse<PageResult<KnowledgeDocument>> list(@RequestParam(required = false) String category,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "20") long pageSize,
                                                           @AuthenticationPrincipal AuthUser authUser) {
        Page<KnowledgeDocument> p = documentService.pageList(category, keyword, page, pageSize, authUser);
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
                                                 @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(documentService.updateVersion(id, title, file, authUser));
    }

    // ==================== 下架 ====================

    /**
     * 下架：DELETE /api/knowledge/docs/{id}（status=0）。需权限码 knowledge:manage。
     */
    @DeleteMapping("/docs/{id}")
    public ApiResponse<Void> offShelf(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthUser authUser) {
        documentService.offShelf(id, authUser);
        return ApiResponse.ok();
    }

    /**
     * 永久删除：DELETE /api/knowledge/docs/{id}/permanent。
     * 删除主记录 + 全部版本历史 + 磁盘文件（不可恢复）。需权限码 knowledge:manage。
     */
    @DeleteMapping("/docs/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthUser authUser) {
        documentService.permanentDelete(id, authUser);
        return ApiResponse.ok();
    }

    // ==================== 版本历史 ====================

    /**
     * 版本历史：GET /api/knowledge/docs/{id}/versions。登录即可。
     */
    @GetMapping("/docs/{id}/versions")
    public ApiResponse<List<KnowledgeDocVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(documentService.versions(id));
    }

    // ==================== 文件下载/预览 ====================

    /**
     * 文件流：GET /api/knowledge/docs/files/{fileName}?download=1（默认 inline 预览）。登录即可。
     * Content-Disposition 使用 RFC 5987 filename* 支持中文文件名。
     * C4：download=1（附件下载）时写入 download_log 计数（doc_id + type=knowledge_doc）；
     * inline 预览不计入下载次数。
     */
    @GetMapping("/docs/files/{fileName}")
    public ResponseEntity<FileSystemResource> file(@PathVariable String fileName,
                                                   @RequestParam(required = false) String download,
                                                   @AuthenticationPrincipal AuthUser authUser,
                                                   HttpServletRequest request) throws IOException {
        Path path = storageService.resolveDocument(fileName);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        boolean forceDownload = "1".equals(download) || "true".equalsIgnoreCase(String.valueOf(download));
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
        String safeName = path.getFileName().toString();
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (forceDownload ? "attachment" : "inline")
                + "; filename=\"" + safeName.replace("\"", "") + "\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .body(new FileSystemResource(path));
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
