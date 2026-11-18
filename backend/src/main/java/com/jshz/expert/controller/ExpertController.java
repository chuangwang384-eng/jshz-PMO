package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.DownloadAuditService;
import com.jshz.expert.service.ExpertService;
import com.jshz.expert.service.ExportService;
import com.jshz.expert.service.OperationLogService;
import com.jshz.expert.service.ResumeParseService;
import com.jshz.expert.common.ParamUtils;
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
import org.springframework.web.multipart.MultipartFile;
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
    private final OperationLogService operationLogService;
    private final ResumeParseService resumeParseService;

    /** Excel（.xlsx）MIME 类型 */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    // ==================== 列表 / 详情 ====================

    /**
     * 分页列表：?keyword=&page=&size= 及可选高级筛选（unit/department/titles/photo/complete/updatedFrom/updatedTo，
     * 组合生效；登录用户一律返回明文，sensitive 不再作为权限门槛，保留登录鉴权）。
     */
    @GetMapping
    public ApiResponse<PageResult<Expert>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String unit,
                                                @RequestParam(required = false) String department,
                                                @RequestParam(required = false) String titles,
                                                @RequestParam(required = false) String photo,
                                                @RequestParam(required = false) String complete,
                                                @RequestParam(required = false) String updatedFrom,
                                                @RequestParam(required = false) String updatedTo,
                                                @RequestParam(required = false) String duplicate,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String sensitive,
                                                @AuthenticationPrincipal AuthUser authUser) {
        boolean full = authUser != null;
        Page<Expert> p = expertService.pageList(keyword, page, size,
                buildFilter(unit, department, titles, photo, complete, updatedFrom, updatedTo, duplicate));
        List<Expert> items = new ArrayList<>();
        for (Expert e : p.getRecords()) {
            items.add(expertService.toView(e, full));
        }
        return ApiResponse.ok(PageResult.of(p.getTotal(), items));
    }

    /**
     * 高级筛选字典：单位/科室/职称去重（登录即可），按出现次数降序。
     */
    @GetMapping("/filters")
    public ApiResponse<Map<String, List<String>>> filters() {
        return ApiResponse.ok(expertService.filterOptions());
    }

    /**
     * 简历解析（新增专家弹窗辅助填充）：PDF/Word/PPT/图片 → 候选字段与头像候选；
     * 简历原文不落盘不留存，审计不含内容；结果仅作候选，提交仍走审批流。
     */
    @PostMapping("/resume/parse")
    public ApiResponse<ResumeParseService.ResumeParseResult> parseResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean contentNameOnly,
            @AuthenticationPrincipal AuthUser authUser,
            HttpServletRequest request) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传简历文件");
        }
        ResumeParseService.ResumeParseResult result =
                resumeParseService.parse(file.getOriginalFilename(), file.getBytes(), !contentNameOnly);
        operationLogService.record(ParamUtils.userIdOf(authUser), ParamUtils.usernameOf(authUser),
                "RESUME_PARSE", "简历解析「" + file.getOriginalFilename() + "」命中 "
                        + result.hitCount() + " 个字段、候选照片 " + result.photoCandidates().size() + " 张",
                "expert", null, request);
        return ApiResponse.ok(result);
    }

    /** 组装筛选条件：解析职称 CSV 与日期范围，非法取值/日期/区间直接 400。 */
    private ExpertService.ExpertFilter buildFilter(String unit, String department, String titles,
                                                   String photo, String complete,
                                                   String updatedFrom, String updatedTo, String duplicate) {
        List<String> titleList = null;
        if (StringUtils.hasText(titles)) {
            titleList = new ArrayList<>();
            for (String t : titles.split(",")) {
                if (StringUtils.hasText(t)) {
                    titleList.add(t.trim());
                }
            }
        }
        if (StringUtils.hasText(photo) && !"uploaded".equals(photo) && !"missing".equals(photo)) {
            throw new BusinessException(400, "photo 取值仅支持 uploaded / missing");
        }
        if (StringUtils.hasText(complete) && !"complete".equals(complete) && !"incomplete".equals(complete)) {
            throw new BusinessException(400, "complete 取值仅支持 complete / incomplete");
        }
        java.time.LocalDate from = parseDate(updatedFrom, "updatedFrom");
        java.time.LocalDate to = parseDate(updatedTo, "updatedTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(400, "更新时间范围无效");
        }
        if (StringUtils.hasText(duplicate) && !"yes".equals(duplicate)) {
            throw new BusinessException(400, "duplicate 取值仅支持 yes");
        }
        return new ExpertService.ExpertFilter(unit, department, titleList, photo, complete, from, to, duplicate);
    }

    private static java.time.LocalDate parseDate(String value, String name) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new BusinessException(400, name + " 日期格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 详情：GET /api/experts/{id}（需求1：登录用户一律返回明文，sensitive 参数保留但不再作为权限门槛）。
     */
    @GetMapping("/{id}")
    public ApiResponse<Expert> detail(@PathVariable Long id,
                                      @RequestParam(required = false) String sensitive,
                                      @AuthenticationPrincipal AuthUser authUser) {
        boolean full = authUser != null;
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
        Expert saved;
        if (request instanceof MultipartHttpServletRequest mpr) {
            saved = expertService.createFromMultipart(mpr, authUser);
        } else {
            saved = expertService.create(readJsonBody(request), authUser);
        }
        operationLogService.record(ParamUtils.userIdOf(authUser), ParamUtils.usernameOf(authUser),
                OperationLogService.ACTION_CREATE_EXPERT, "新增专家「" + saved.getName() + "」",
                "EXPERT", saved.getId(), request);
        return ApiResponse.ok(saved);
    }

    /**
     * 修改：支持 JSON（中文键）或 multipart；换照片时物理删除旧文件。
     */
    @PutMapping("/{id}")
    public ApiResponse<Expert> update(@PathVariable Long id, HttpServletRequest request,
                                      @AuthenticationPrincipal AuthUser authUser) {
        Expert saved;
        if (request instanceof MultipartHttpServletRequest mpr) {
            saved = expertService.updateFromMultipart(id, mpr, authUser);
        } else {
            saved = expertService.update(id, readJsonBody(request), authUser);
        }
        operationLogService.record(ParamUtils.userIdOf(authUser), ParamUtils.usernameOf(authUser),
                OperationLogService.ACTION_UPDATE_EXPERT, "修改专家「" + saved.getName() + "」",
                "EXPERT", id, request);
        return ApiResponse.ok(saved);
    }

    /**
     * 删除：物理删除三类照片文件。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id,
                                                   @AuthenticationPrincipal AuthUser authUser,
                                                   HttpServletRequest request) {
        Expert target = expertService.getById(id);
        expertService.deleteExpert(id);
        String name = target == null ? String.valueOf(id) : target.getName();
        operationLogService.record(ParamUtils.userIdOf(authUser), ParamUtils.usernameOf(authUser),
                OperationLogService.ACTION_DELETE_EXPERT, "删除专家「" + name + "」", "EXPERT", id, request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", id);
        return ApiResponse.ok(data);
    }

    // ==================== 导入导出（对齐 demo） ====================

    /**
     * 勾选导出 Excel：GET /api/experts/selected/export?ids=1,2,3（需求1：全员明文导出）。
     */
    @GetMapping("/selected/export")
    public void selectedExport(@RequestParam("ids") String ids,
                               @RequestParam(required = false) String sensitive,
                               @AuthenticationPrincipal AuthUser authUser,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        List<Expert> experts = loadByIds(ids);
        boolean full = true;
        byte[] xlsx = exportService.expertsToXlsx(experts, full);
        exportService.audit(experts, "selected_xlsx", authUser, request);
        writeAttachment(response, xlsx, "selected_experts.xlsx", XLSX_MEDIA_TYPE);
    }

    /**
     * 勾选导出 ZIP：GET /api/experts/selected/zip?ids=1,2,3（需求1：Excel + 三类照片，全员明文）。
     */
    @GetMapping("/selected/zip")
    public void selectedZip(@RequestParam("ids") String ids,
                            @RequestParam(required = false) String sensitive,
                            @AuthenticationPrincipal AuthUser authUser,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        List<Expert> experts = loadByIds(ids);
        boolean full = true;
        byte[] zip = exportService.buildZip(experts, full, "selected_zip", authUser, request);
        writeAttachment(response, zip, "selected_experts.zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 全部专家 Excel（完整字段，员工也可下载全量敏感数据，下载留痕）。
     */
    @GetMapping("/export")
    public void exportAll(@AuthenticationPrincipal AuthUser authUser,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] xlsx = exportService.expertsToXlsx(experts, true);
        exportService.audit(experts, "full_xlsx", authUser, request);
        writeAttachment(response, xlsx, "experts_all.xlsx", XLSX_MEDIA_TYPE);
    }

    /**
     * 全部专家 ZIP（完整 ZIP：完整 Excel + 三类照片原图）。
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
     * 全部专家 ZIP（需求1：Excel 全员明文 + 照片原图，不再区分明文/脱敏分支）。
     */
    @GetMapping("/export/employee")
    public void exportEmployee(@AuthenticationPrincipal AuthUser authUser,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] zip = exportService.buildZip(experts, true, "employee_zip", authUser, request);
        writeAttachment(response, zip, "experts_all_" + LocalDate.now() + ".zip", MediaType.APPLICATION_OCTET_STREAM);
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
     * 导入模板 Excel。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        byte[] xlsx = exportService.templateXlsx();
        writeAttachment(response, xlsx, "import_template.xlsx", XLSX_MEDIA_TYPE);
    }

    /**
     * 导入 Excel（.xlsx）：multipart 字段名 file。
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(MultipartHttpServletRequest request,
                                                        @AuthenticationPrincipal AuthUser authUser) {
        var file = request.getFile("file");
        Map<String, Object> result = exportService.importXlsx(file, authUser);
        operationLogService.record(ParamUtils.userIdOf(authUser), ParamUtils.usernameOf(authUser),
                OperationLogService.ACTION_IMPORT_EXPERT, "导入专家 Excel " + result,
                "EXPERT", null, request);
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
