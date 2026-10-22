package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.DownloadAuditService;
import com.jshz.expert.service.ExpertService;
import com.jshz.expert.service.ExportService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专家接口全集（正式版 V1.001，对齐 demo /api/experts 与设计文档）。
 * 返回 {total, items} 结构、接口键为中文、查看默认脱敏（前3后4）。
 */
@RestController
@RequestMapping("/api/experts")
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertService expertService;
    private final ExportService exportService;
    private final DownloadAuditService downloadAuditService;

    // ==================== 列表 / 详情 ====================

    /**
     * 分页列表：?keyword=&page=&size=&sensitive=1（仅管理员 sensitive=1 返回全量）。
     */
    @GetMapping
    public ApiResponse<PageResult<Expert>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String sensitive,
                                                @AuthenticationPrincipal AuthUser authUser) {
        boolean full = authUser != null && authUser.isAdmin() && "1".equals(sensitive);
        Page<Expert> p = expertService.pageList(keyword, page, size);
        List<Expert> items = new ArrayList<>();
        for (Expert e : p.getRecords()) {
            items.add(expertService.toView(e, full));
        }
        return ApiResponse.ok(PageResult.of(p.getTotal(), items));
    }

    /**
     * 详情：GET /api/experts/{id}?sensitive=1（仅管理员全量）。
     */
    @GetMapping("/{id}")
    public ApiResponse<Expert> detail(@PathVariable Long id,
                                      @RequestParam(required = false) String sensitive,
                                      @AuthenticationPrincipal AuthUser authUser) {
        boolean full = authUser != null && authUser.isAdmin() && "1".equals(sensitive);
        return ApiResponse.ok(expertService.detail(id, full));
    }

    /**
     * 单个专家下载次数：GET /api/experts/{id}/download-count
     */
    @GetMapping("/{id}/download-count")
    public ApiResponse<Map<String, Object>> downloadCount(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", downloadAuditService.countByExpertId(id));
        return ApiResponse.ok(data);
    }

    // ==================== 新增 / 修改 / 删除 ====================

    /**
     * 新增：支持 JSON（中文键）或 multipart（中文字段 + photo/idCardFront/idCardBack 照片文件）。
     */
    @PostMapping
    public ApiResponse<Expert> create(HttpServletRequest request,
                                      @AuthenticationPrincipal AuthUser authUser) {
        if (request instanceof MultipartHttpServletRequest mpr) {
            return ApiResponse.ok(expertService.createFromMultipart(mpr, authUser));
        }
        Expert expert = readJsonBody(request);
        return ApiResponse.ok(expertService.create(expert, authUser));
    }

    /**
     * 修改：支持 JSON（中文键）或 multipart；换照片时物理删除旧文件。
     */
    @PutMapping("/{id}")
    public ApiResponse<Expert> update(@PathVariable Long id, HttpServletRequest request,
                                      @AuthenticationPrincipal AuthUser authUser) {
        if (request instanceof MultipartHttpServletRequest mpr) {
            return ApiResponse.ok(expertService.updateFromMultipart(id, mpr, authUser));
        }
        Expert expert = readJsonBody(request);
        return ApiResponse.ok(expertService.update(id, expert, authUser));
    }

    /**
     * 删除：物理删除三类照片文件。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        expertService.deleteExpert(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", id);
        return ApiResponse.ok(data);
    }

    // ==================== 导入导出（对齐 demo） ====================

    /**
     * 勾选导出 CSV：GET /api/experts/selected/export?ids=1,2,3&sensitive=1
     */
    @GetMapping("/selected/export")
    public void selectedExport(@RequestParam("ids") String ids,
                               @RequestParam(required = false) String sensitive,
                               @AuthenticationPrincipal AuthUser authUser,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        List<Expert> experts = loadByIds(ids);
        boolean full = "1".equals(sensitive);
        byte[] csv = exportService.expertsToCsv(experts, full);
        exportService.audit(experts, "selected_csv", authUser, request);
        writeAttachment(response, csv, "selected_experts.csv",
                MediaType.parseMediaType("text/csv;charset=UTF-8"));
    }

    /**
     * 勾选导出 ZIP：GET /api/experts/selected/zip?ids=1,2,3&sensitive=1（CSV + 三类照片）
     */
    @GetMapping("/selected/zip")
    public void selectedZip(@RequestParam("ids") String ids,
                            @RequestParam(required = false) String sensitive,
                            @AuthenticationPrincipal AuthUser authUser,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        List<Expert> experts = loadByIds(ids);
        boolean full = "1".equals(sensitive);
        byte[] zip = exportService.buildZip(experts, full, "selected_zip", authUser, request);
        writeAttachment(response, zip, "selected_experts.zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 全部专家 CSV（完整字段，员工也可下载全量敏感数据，下载留痕）。
     */
    @GetMapping("/export")
    public void exportAll(@AuthenticationPrincipal AuthUser authUser,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] csv = exportService.expertsToCsv(experts, true);
        exportService.audit(experts, "full_csv", authUser, request);
        writeAttachment(response, csv, "experts_all.csv", MediaType.parseMediaType("text/csv;charset=UTF-8"));
    }

    /**
     * 全部专家 ZIP（管理员完整 ZIP：完整 CSV + 三类照片原图）。
     */
    @GetMapping("/export/full")
    public void exportFull(@AuthenticationPrincipal AuthUser authUser,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] zip = exportService.buildZip(experts, true, "full_zip", authUser, request);
        writeAttachment(response, zip, "experts_full_" + LocalDate.now() + ".zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 员工脱敏 ZIP：脱敏 CSV + 照片原图（对齐 demo /api/export）。
     */
    @GetMapping("/export/employee")
    public void exportEmployee(@AuthenticationPrincipal AuthUser authUser,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] zip = exportService.buildZip(experts, false, "employee_zip", authUser, request);
        writeAttachment(response, zip, "experts_masked_" + LocalDate.now() + ".zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 下载说明 Markdown：GET /api/experts/export/md?full=1
     */
    @GetMapping("/export/md")
    public void exportMd(@RequestParam(required = false) String full,
                         @AuthenticationPrincipal AuthUser authUser,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] md = exportService.buildMarkdown(experts, "1".equals(full), authUser, request);
        writeAttachment(response, md, "download_guide.md", MediaType.parseMediaType("text/markdown;charset=UTF-8"));
    }

    /**
     * 导入模板 CSV。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        byte[] csv = exportService.templateCsv();
        writeAttachment(response, csv, "import_template.csv",
                MediaType.parseMediaType("text/csv;charset=UTF-8"));
    }

    /**
     * 导入 CSV：multipart 字段名 file。
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importCsv(MultipartHttpServletRequest request,
                                                      @AuthenticationPrincipal AuthUser authUser) {
        var file = request.getFile("file");
        Map<String, Object> result = exportService.importCsv(file, authUser);
        return ApiResponse.ok(result);
    }

    // ==================== 兼容路径（demo /api/export 系列） ====================

    /**
     * demo 兼容：GET /api/export（员工脱敏 ZIP）由 ExportCompatController 提供。
     */

    // ==================== 私有方法 ====================

    private Expert readJsonBody(HttpServletRequest request) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(request.getInputStream(), Expert.class);
        } catch (IOException e) {
            throw new BusinessException("请求体解析失败: " + e.getMessage());
        }
    }

    private List<Expert> loadByIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            throw new BusinessException("缺少 ids 参数");
        }
        List<Long> idList = new ArrayList<>();
        for (String part : ids.split(",")) {
            if (StringUtils.hasText(part)) {
                idList.add(Long.valueOf(part.trim()));
            }
        }
        if (idList.isEmpty()) {
            throw new BusinessException("ids 参数为空");
        }
        List<Expert> experts = expertService.listByIds(idList);
        if (experts.isEmpty()) {
            throw new BusinessException("未找到对应专家");
        }
        return experts;
    }

    private void writeAttachment(HttpServletResponse response, byte[] data, String fileName,
                                 MediaType mediaType) throws IOException {
        response.setContentType(mediaType.toString());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }
}
