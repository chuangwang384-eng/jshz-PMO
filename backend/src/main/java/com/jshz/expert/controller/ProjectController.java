package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectContract;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.entity.ProjectTemplate;
import com.jshz.expert.entity.ProjectSchedule;
import com.jshz.expert.entity.ProjectSpeaker;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectService;
import com.jshz.expert.service.ProjectStorageService;
import com.jshz.expert.service.ScheduleParseService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 项目管理模块控制器（需求文档 2026-10-27）。
 * 路由：/api/project（列表/CRUD）、/api/project/{id}（详情/阶段/取消/专家/供应商/资料/合同/预算）、
 * /api/project/export（导出 xlsx）、/api/project/config/**（类型/阶段/模板配置）。
 * 权限由 permission 表 type=2 数据驱动（DbPermissionFilter）控制：写操作绑定 project:manage，
 * 只读接口未命中规则即登录可见（等同 project:view 全员默认）；阶段推进为项目级授权（负责人+执行人）。
 */
@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 分页列表（筛选：keyword/status/startBegin/startEnd/endBegin/endEnd/ownerId/category/team/cityId/assistantId；类型/执行人筛选已随概念删除）。 */
    @GetMapping
    public ApiResponse<Page<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String startBegin,
                                                       @RequestParam(required = false) String startEnd,
                                                       @RequestParam(required = false) String endBegin,
                                                       @RequestParam(required = false) String endEnd,
                                                       @RequestParam(required = false) Long ownerId,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(required = false) String team,
                                                       @RequestParam(required = false) Long cityId,
                                                       @RequestParam(required = false) Long assistantId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.pageProjects(keyword, status, startBegin, startEnd, endBegin, endEnd,
                ownerId, category, team, cityId, assistantId, page, size, user));
    }

    /** 创建项目。 */
    @PostMapping
    public ApiResponse<Project> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.create(body, user));
    }

    /** 详情（含关联/预算汇总/时间线/阶段模板）。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.detail(id, user));
    }

    /** 编辑项目。 */
    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.update(id, body, user));
    }

    /** 软删除。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        projectService.deleteSoft(id, user);
        return ApiResponse.ok(null);
    }

    /** 推进/回退阶段（校验必填模板；可跳步）。 */
    @PutMapping("/{id}/phase")
    public ApiResponse<Void> changePhase(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Long phaseId = ParamUtils.parseLong(body == null ? null : body.get("phaseId"), null);
        if (phaseId == null) {
            throw new BusinessException("目标阶段不能为空或非法");
        }
        Object reasonObj = body == null ? null : body.get("reason");
        String reason = reasonObj == null ? null : String.valueOf(reasonObj);
        projectService.changePhase(id, phaseId, reason, user);
        return ApiResponse.ok(null);
    }

    /** 取消项目（必填原因）。 */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object reason = body == null ? null : body.get("reason");
        projectService.cancel(id, reason == null ? null : String.valueOf(reason), user);
        return ApiResponse.ok(null);
    }

    /**
     * 项目文件流（Owner 2026-11-12 在线预览/下载）：GET /api/project/files/{fileName}?download=1&preview=true。
     * 登录即可；preview=true 时 Office 经 DocPreviewService 转 PDF（失败返回结构化 JSON），PDF/图片原流。
     */
    @GetMapping("/files/{fileName}")
    public org.springframework.http.ResponseEntity<?> projectFile(
            @PathVariable String fileName,
            @RequestParam(required = false) String download,
            @RequestParam(required = false) String preview,
            @AuthenticationPrincipal AuthUser user) throws java.io.IOException {
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        java.nio.file.Path path = resolveReadableStoredFile(fileName, user);
        if (path == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        boolean forceDownload = "1".equals(download) || "true".equalsIgnoreCase(String.valueOf(download));
        boolean previewMode = !forceDownload && ("true".equalsIgnoreCase(preview) || "1".equals(preview));
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        org.springframework.http.MediaType mediaType = projectFileMediaType(ext);
        if (previewMode && com.jshz.expert.service.DocPreviewService.isOffice(ext)) {
            try {
                String cacheKey = "proj_" + fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                path = docPreviewService.getPreviewFileFor(path, cacheKey, ext);
                mediaType = org.springframework.http.MediaType.APPLICATION_PDF;
            } catch (BusinessException e) {
                return projectPreviewError("CONVERT_FAILED", "文档转换失败，可下载查看");
            } catch (Exception e) {
                return projectPreviewError("PREVIEW_FAILED", "预览失败，可下载查看");
            }
        }
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(java.nio.file.Files.size(path));
        if (forceDownload) {
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
        } else {
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline");
        }
        return new org.springframework.http.ResponseEntity<>(
                new org.springframework.core.io.InputStreamResource(java.nio.file.Files.newInputStream(path)),
                headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 项目文件 Excel 工作簿分页预览（2026-11-17）：复用知识星球 ExcelPreviewService（POI 100行×40列分页），
     * 权限口径与 GET /files/{fileName} 一致（报价文件走项目阅读权限），前端复用 KnowledgeWorkbookPreview。
     */
    @GetMapping("/files/{fileName}/workbook")
    public ApiResponse<com.jshz.expert.service.ExcelPreviewService.Preview> projectWorkbook(
            @PathVariable String fileName,
            @RequestParam(required = false) Integer sheet,
            @RequestParam(defaultValue = "0") int rowPage,
            @RequestParam(defaultValue = "0") int columnPage,
            @AuthenticationPrincipal AuthUser user) {
        java.nio.file.Path path = resolveReadableStoredFile(fileName, user);
        if (path == null) {
            throw new BusinessException("文件不存在或已失效");
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        if (!"xls".equals(ext) && !"xlsx".equals(ext)) {
            throw new BusinessException("该文件不是 Excel 工作簿");
        }
        return ApiResponse.ok(excelPreviewService.read(path, sheet, rowPage, columnPage));
    }

    /** 报价文件鉴权 + 解析存储文件（防穿越后不存在/非普通文件返回 null）；/files 两个端点共用。 */
    private java.nio.file.Path resolveReadableStoredFile(String fileName, AuthUser user) {
        if (fileName.startsWith(ProjectStorageService.QUOTE_FILE_PREFIX)) {
            projectService.requireQuoteFileReadable(fileName, user);
        }
        java.nio.file.Path path = projectService.resolveStoredFile(fileName);
        return path != null && java.nio.file.Files.exists(path) && java.nio.file.Files.isRegularFile(path) ? path : null;
    }

    private org.springframework.http.MediaType projectFileMediaType(String ext) {
        return switch (ext) {
            case "pdf" -> org.springframework.http.MediaType.APPLICATION_PDF;
            case "png" -> org.springframework.http.MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> org.springframework.http.MediaType.IMAGE_JPEG;
            case "gif" -> org.springframework.http.MediaType.IMAGE_GIF;
            case "webp" -> org.springframework.http.MediaType.parseMediaType("image/webp");
            case "bmp" -> org.springframework.http.MediaType.parseMediaType("image/bmp");
            default -> org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private org.springframework.http.ResponseEntity<?> projectPreviewError(String code, String message) {
        java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body);
    }

    /** 恢复已取消项目（Owner 2026-11-10：与取消对称；仅 CANCELED 可恢复，负责人/创建人权限，阶段保持不变）。 */
    @PutMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        projectService.restore(id, user);
        return ApiResponse.ok(null);
    }

    /** 时间线（operation_log target_type=PROJECT 倒序）。 */
    @GetMapping("/{id}/timeline")
    public ApiResponse<List<?>> timeline(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.timeline(id, user));
    }

    /** 专家名单。 */
    @GetMapping("/{id}/experts")
    public ApiResponse<List<Map<String, Object>>> experts(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.listExperts(id, user));
    }

    /** 调派专家。 */
    @PostMapping("/{id}/experts")
    public ApiResponse<Void> addExpert(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Long expertId = ParamUtils.parseLong(body == null ? null : body.get("expertId"), null);
        if (expertId == null) {
            throw new BusinessException("专家ID不能为空或非法");
        }
        Object role = body == null ? null : body.get("role");
        projectService.addExpert(id, expertId, role == null ? null : String.valueOf(role), user);
        return ApiResponse.ok(null);
    }

    /** 移出专家（留痕）。 */
    @DeleteMapping("/{id}/experts/{expertId}")
    public ApiResponse<Void> removeExpert(@PathVariable Long id, @PathVariable Long expertId, @AuthenticationPrincipal AuthUser user) {
        projectService.removeExpert(id, expertId, user);
        return ApiResponse.ok(null);
    }

    /** 新建专家卡并自动关联（留痕）。 */
    @PostMapping("/{id}/experts/new-card")
    public ApiResponse<Expert> createExpertCard(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.createExpertCard(id, body, user));
    }

    /** 供应商名单。 */
    @GetMapping("/{id}/suppliers")
    public ApiResponse<List<Map<String, Object>>> suppliers(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.listSuppliers(id, user));
    }

    /** 关联供应商（用途按类型自动带出）。 */
    @PostMapping("/{id}/suppliers")
    public ApiResponse<Void> addSupplier(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Long supplierId = ParamUtils.parseLong(body == null ? null : body.get("supplierId"), null);
        if (supplierId == null) {
            throw new BusinessException("供应商ID不能为空或非法");
        }
        projectService.addSupplier(id, supplierId, user);
        return ApiResponse.ok(null);
    }

    /** 移出供应商（留痕）。 */
    @DeleteMapping("/{id}/suppliers/{supplierId}")
    public ApiResponse<Void> removeSupplier(@PathVariable Long id, @PathVariable Long supplierId, @AuthenticationPrincipal AuthUser user) {
        projectService.removeSupplier(id, supplierId, user);
        return ApiResponse.ok(null);
    }

    /** 分阶段资料列表（phaseId 可空）。 */
    @GetMapping("/{id}/docs")
    public ApiResponse<List<Map<String, Object>>> docs(@PathVariable Long id,
                                                       @RequestParam(required = false) Long phaseId,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.listDocs(id, phaseId, user));
    }

    /** 上传分阶段资料（无类型/大小限制）。 */
    @PostMapping("/{id}/docs")
    public ApiResponse<ProjectDoc> uploadDoc(@PathVariable Long id,
                                             @RequestParam("phaseId") Long phaseId,
                                             @RequestParam("file") MultipartFile file,
                                             @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.uploadDoc(id, phaseId, file, user));
    }

    /** 删除分阶段资料（软删留痕）。 */
    @DeleteMapping("/{id}/docs/{docId}")
    public ApiResponse<Void> deleteDoc(@PathVariable Long id, @PathVariable Long docId, @AuthenticationPrincipal AuthUser user) {
        projectService.deleteDoc(id, docId, user);
        return ApiResponse.ok(null);
    }

    /** 保存富文本（自定义文本模板）。 */
    @PutMapping("/{id}/docs/richtext")
    public ApiResponse<Void> saveRichText(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Long phaseId = ParamUtils.parseLong(body == null ? null : body.get("phaseId"), null);
        Long templateId = ParamUtils.parseLong(body == null ? null : body.get("templateId"), null);
        if (phaseId == null || templateId == null) {
            throw new BusinessException("阶段或模板参数缺失，无法保存文本");
        }
        Object content = body == null ? null : body.get("content");
        projectService.saveRichText(id, phaseId, templateId, content == null ? null : String.valueOf(content), user);
        return ApiResponse.ok(null);
    }

    /** 登记预算金额。 */
    @PutMapping("/{id}/budget")
    public ApiResponse<Void> registerBudget(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object amount = body == null ? null : body.get("amount");
        projectService.registerBudget(id, amount == null ? null : new BigDecimal(String.valueOf(amount)), user);
        return ApiResponse.ok(null);
    }

    /** 合同列表。 */
    @GetMapping("/{id}/contracts")
    public ApiResponse<List<Map<String, Object>>> contracts(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.listContracts(id, user));
    }

    /** 登记合同（自动带出专家敏感字段）。 */
    @PostMapping("/{id}/contracts")
    public ApiResponse<ProjectContract> createContract(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Long expertId = ParamUtils.parseLong(body == null ? null : body.get("expertId"), null);
        if (expertId == null) {
            throw new BusinessException("专家ID不能为空或非法");
        }
        Object fee = body == null ? null : body.get("fee");
        return ApiResponse.ok(projectService.createContract(id, expertId, fee == null ? null : new BigDecimal(String.valueOf(fee)), user));
    }

    /** 修改合同敏感字段（同步回写 expert_info 留痕）。 */
    @PutMapping("/{id}/contracts/{contractId}/sensitive")
    public ApiResponse<Void> updateContractSensitive(@PathVariable Long id, @PathVariable Long contractId,
                                                     @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        projectService.updateContractSensitive(id, contractId, body, user);
        return ApiResponse.ok(null);
    }

    /** 上传合同文件。 */
    @PostMapping("/{id}/contracts/{contractId}/file")
    public ApiResponse<Void> uploadContractFile(@PathVariable Long id, @PathVariable Long contractId,
                                                @RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal AuthUser user) {
        projectService.uploadContractFile(id, contractId, file, user);
        return ApiResponse.ok(null);
    }

    /** 发起合同审批（复用现有审批流）。 */
    @PostMapping("/{id}/contracts/{contractId}/approval")
    public ApiResponse<Application> submitContractApproval(@PathVariable Long id, @PathVariable Long contractId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.submitContractApproval(id, contractId, user));
    }

    /** 导出 xlsx（含专家/供应商明细；DOWNLOAD 留痕；类型/执行人列已随概念删除）。 */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String startBegin,
                       @RequestParam(required = false) String startEnd,
                       @RequestParam(required = false) String endBegin,
                       @RequestParam(required = false) String endEnd,
                       @RequestParam(required = false) Long ownerId,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String team,
                       @RequestParam(required = false) Long cityId,
                       @RequestParam(required = false) Long assistantId,
                       HttpServletResponse response,
                       @AuthenticationPrincipal AuthUser user) throws Exception {
        Workbook wb = projectService.buildExportWorkbook(keyword, status, startBegin, startEnd, endBegin, endEnd,
                ownerId, category, team, cityId, assistantId, user);
        String fileName = "项目列表_" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");
        wb.write(response.getOutputStream());
        wb.close();
    }

    /** 负责人候选（拥有 PROJECT_OWNER 角色的用户 + 超级管理员）。 */
    @GetMapping("/owners")
    public ApiResponse<List<Map<String, Object>>> owners() {
        return ApiResponse.ok(projectService.listProjectOwners());
    }

    // 执行人候选接口已随执行人概念删除（2026-11-09 流程重塑）。

    /** 阶段选项（激活阶段字典，登录即可；阶段筛选/展示用，非配置管理）。 */
    @GetMapping("/phase-options")
    public ApiResponse<List<ProjectPhase>> phaseOptions() {
        return ApiResponse.ok(projectService.listActivePhases());
    }

    /** 用户选项（全部在职用户，仅 id/姓名，登录即可；负责人/协助人筛选与选择用，替代已删的 /executors）。 */
    @GetMapping("/user-options")
    public ApiResponse<List<Map<String, Object>>> userOptions() {
        return ApiResponse.ok(projectService.listUserOptions());
    }

    // ==================== 配置管理：已随项目类型概念停用（2026-11-09 流程重塑） ====================
    // 类型决定阶段流程的配置体系整体下线（表保留观察，下版删除）；前端配置页同步移除。

    /** 410：项目类型/阶段流程配置已停用。 */
    private ApiResponse<Object> configRetired() {
        throw new BusinessException(410, "项目类型/阶段流程配置已停用（2026-11-09 项目流程重塑）：类型概念已删除，项目固定走新流程");
    }

    @GetMapping("/config/types")
    public ApiResponse<Object> listTypes() { return configRetired(); }

    @PostMapping("/config/types")
    public ApiResponse<Object> createType() { return configRetired(); }

    @PutMapping("/config/types/{id}")
    public ApiResponse<Object> updateType(@PathVariable Long id) { return configRetired(); }

    @DeleteMapping("/config/types/{id}")
    public ApiResponse<Object> deleteType(@PathVariable Long id) { return configRetired(); }

    @GetMapping("/config/phases")
    public ApiResponse<Object> listPhases() { return configRetired(); }

    @PostMapping("/config/phases")
    public ApiResponse<Object> createPhase() { return configRetired(); }

    @PutMapping("/config/phases/{id}")
    public ApiResponse<Object> updatePhase(@PathVariable Long id) { return configRetired(); }

    @DeleteMapping("/config/phases/{id}")
    public ApiResponse<Object> deletePhase(@PathVariable Long id) { return configRetired(); }

    @GetMapping("/config/templates")
    public ApiResponse<Object> listTemplates() { return configRetired(); }

    @PostMapping("/config/templates")
    public ApiResponse<Object> createTemplate() { return configRetired(); }

    @PutMapping("/config/templates/{id}")
    public ApiResponse<Object> updateTemplate(@PathVariable Long id) { return configRetired(); }

    @DeleteMapping("/config/templates/{id}")
    public ApiResponse<Object> deleteTemplate(@PathVariable Long id) { return configRetired(); }

    @GetMapping("/config/phases/{phaseId}/templates")
    public ApiResponse<Object> listPhaseTemplates(@PathVariable Long phaseId) { return configRetired(); }

    @PutMapping("/config/phases/{phaseId}/templates")
    public ApiResponse<Object> savePhaseTemplates(@PathVariable Long phaseId) { return configRetired(); }

    @GetMapping("/config/types/{typeId}/phases")
    public ApiResponse<Object> listTypePhases(@PathVariable Long typeId) { return configRetired(); }

    @PutMapping("/config/types/{typeId}/phases")
    public ApiResponse<Object> saveTypePhases(@PathVariable Long typeId) { return configRetired(); }

    @GetMapping("/config/types/{typeId}/phases/{phaseId}/templates")
    public ApiResponse<Object> listTypePhaseTemplates(@PathVariable Long typeId, @PathVariable Long phaseId) { return configRetired(); }

    @PutMapping("/config/types/{typeId}/phases/{phaseId}/templates")
    public ApiResponse<Object> saveTypePhaseTemplates(@PathVariable Long typeId, @PathVariable Long phaseId) { return configRetired(); }

    // ==================== 日程上传（2026-11-09 流程重塑第一阶段） ====================

    private final ScheduleParseService scheduleParseService;
    private final com.jshz.expert.service.DocPreviewService docPreviewService;
    private final com.jshz.expert.service.ExcelPreviewService excelPreviewService;

    /** 上传日程文件（pdf/doc/docx/ppt/pptx/jpg/png，≤20MB；覆盖当前生效份）。 */
    @PostMapping("/{id}/schedule")
    public ApiResponse<ProjectSchedule> uploadSchedule(@PathVariable Long id,
                                                       @RequestParam("file") MultipartFile file,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.upload(id, file, user));
    }

    /** 日程与参会专家汇总（当前日程 + 候选 + 正式记录）。 */
    @GetMapping("/{id}/schedule")
    public ApiResponse<Map<String, Object>> scheduleOverview(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.overview(id, user));
    }

    /**
     * 删除日程阶段附件：软删留痕；若删的是当前生效日程文件，联动硬删日程记录与未确认候选
     * （人工确认成果保留），文件无其他引用时物理删（Owner 2026-11-10：附件可删 + 不做历史版本）。
     */
    @DeleteMapping("/{id}/schedule/docs/{docId}")
    public ApiResponse<Void> deleteScheduleDoc(@PathVariable Long id, @PathVariable Long docId,
                                               @AuthenticationPrincipal AuthUser user) {
        scheduleParseService.deleteScheduleDoc(id, docId, user);
        return ApiResponse.ok(null);
    }

    /** 触发解析（规则打底 + 本地大模型兜底，结果为候选，须人工确认）。 */
    @PostMapping("/{id}/schedule/{scheduleId}/parse")
    public ApiResponse<List<ProjectSpeaker>> parseSchedule(@PathVariable Long id, @PathVariable Long scheduleId,
                                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.parse(id, scheduleId, user));
    }

    /** 人工确认保存参会专家（整表覆盖式提交正式记录）。 */
    @PutMapping("/{id}/speakers")
    public ApiResponse<List<ProjectSpeaker>> confirmSpeakers(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        Object items = body == null ? null : body.get("speakers");
        List<Map<String, Object>> list = items instanceof List<?> l
                ? l.stream().filter(o -> o instanceof Map).map(o -> (Map<String, Object>) o).collect(java.util.stream.Collectors.toList())
                : List.of();
        return ApiResponse.ok(scheduleParseService.confirm(id, list, user));
    }

    /** 手动补录一条参会专家（解析失败/遗漏兜底）。 */
    @PostMapping("/{id}/speakers")
    public ApiResponse<ProjectSpeaker> addSpeaker(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.addSpeaker(id, body, user));
    }

    /** 删除一条参会专家记录（候选或正式）。 */
    @DeleteMapping("/{id}/speakers/{speakerId}")
    public ApiResponse<Void> deleteSpeaker(@PathVariable Long id, @PathVariable Long speakerId,
                                           @AuthenticationPrincipal AuthUser user) {
        scheduleParseService.deleteSpeaker(id, speakerId, user);
        return ApiResponse.ok(null);
    }

    /**
     * 批量简历建档（库外专家入档）：多份简历解析→建档（姓名和单位必填）
     * →自动关联项目专家名单→按姓名回绑本项目参会专家。单份失败不影响其余，逐条返回结果。
     */
    @PostMapping("/{id}/speakers/archive-resumes")
    public ApiResponse<List<Map<String, Object>>> archiveResumes(@PathVariable Long id,
                                                                 @RequestParam("files") MultipartFile[] files,
                                                                 @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.archiveByResumes(id, List.of(files), user));
    }

    /** 手动建档单条（成员级；与批量同一条建档路径，只收姓名/单位/职称/简介/联系方式）。 */
    @PostMapping(value = "/{id}/speakers/archive-manual", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> archiveManual(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.archiveManual(id, body, null, user));
    }

    @PostMapping(value = "/{id}/speakers/archive-manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> archiveWithPhoto(@PathVariable Long id,
                                                            @RequestPart("data") Map<String, Object> body,
                                                            @RequestPart("photo") MultipartFile photo,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(scheduleParseService.archiveManual(id, body, photo, user));
    }

    @PutMapping("/{id}/experts/{expertId}/fee")
    public ApiResponse<Void> saveExpertFee(@PathVariable Long id, @PathVariable Long expertId,
                                           @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object fee = body == null ? null : body.get("fee");
        projectService.saveExpertFee(id, expertId, fee == null ? null : Double.valueOf(String.valueOf(fee)), user);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/suppliers/{supplierId}/fee")
    public ApiResponse<Void> saveSupplierFee(@PathVariable Long id, @PathVariable Long supplierId,
                                             @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object fee = body == null ? null : body.get("fee");
        projectService.saveSupplierFee(id, supplierId, fee == null ? null : Double.valueOf(String.valueOf(fee)), user);
        return ApiResponse.ok(null);
    }
}
