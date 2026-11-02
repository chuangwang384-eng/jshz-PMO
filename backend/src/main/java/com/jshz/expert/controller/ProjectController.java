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
import com.jshz.expert.entity.ProjectType;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 分页列表（筛选：status/startBegin/startEnd/endBegin/endEnd/ownerId/category/typeId/executorId）。 */
    @GetMapping
    public ApiResponse<Page<Map<String, Object>>> page(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String startBegin,
                                                       @RequestParam(required = false) String startEnd,
                                                       @RequestParam(required = false) String endBegin,
                                                       @RequestParam(required = false) String endEnd,
                                                       @RequestParam(required = false) Long ownerId,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(required = false) Long typeId,
                                                       @RequestParam(required = false) Long executorId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projectService.pageProjects(status, startBegin, startEnd, endBegin, endEnd,
                ownerId, category, typeId, executorId, page, size, user));
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

    /** 时间线（operation_log target_type=PROJECT 倒序）。 */
    @GetMapping("/{id}/timeline")
    public ApiResponse<List<?>> timeline(@PathVariable Long id) {
        return ApiResponse.ok(projectService.timeline(id));
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

    /** 导出 xlsx（含专家/供应商明细；DOWNLOAD 留痕）。 */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String startBegin,
                       @RequestParam(required = false) String startEnd,
                       @RequestParam(required = false) String endBegin,
                       @RequestParam(required = false) String endEnd,
                       @RequestParam(required = false) Long ownerId,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) Long typeId,
                       @RequestParam(required = false) Long executorId,
                       HttpServletResponse response,
                       @AuthenticationPrincipal AuthUser user) throws Exception {
        Workbook wb = projectService.buildExportWorkbook(status, startBegin, startEnd, endBegin, endEnd,
                ownerId, category, typeId, executorId, user);
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

    /** 执行人候选（全部在职用户，仅 id/姓名；登录即可，供执行人筛选/选择，替代需 user:manage 的 /api/users）。 */
    @GetMapping("/executors")
    public ApiResponse<List<Map<String, Object>>> executors() {
        return ApiResponse.ok(projectService.listProjectExecutors());
    }

    // ==================== 配置管理（project:manage） ====================

    @GetMapping("/config/types")
    public ApiResponse<List<ProjectType>> listTypes() {
        return ApiResponse.ok(projectService.listTypes());
    }

    @PostMapping("/config/types")
    public ApiResponse<ProjectType> createType(@RequestBody Map<String, Object> body) {
        Object name = body == null ? null : body.get("name");
        Object sort = body == null ? null : body.get("sort");
        return ApiResponse.ok(projectService.createType(name == null ? null : String.valueOf(name),
                sort == null ? null : Integer.valueOf(String.valueOf(sort))));
    }

    @PutMapping("/config/types/{id}")
    public ApiResponse<ProjectType> updateType(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object name = body == null ? null : body.get("name");
        Object sort = body == null ? null : body.get("sort");
        return ApiResponse.ok(projectService.updateType(id, name == null ? null : String.valueOf(name),
                sort == null ? null : Integer.valueOf(String.valueOf(sort))));
    }

    @DeleteMapping("/config/types/{id}")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        projectService.deleteType(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/config/phases")
    public ApiResponse<List<ProjectPhase>> listPhases() {
        return ApiResponse.ok(projectService.listPhases());
    }

    @PostMapping("/config/phases")
    public ApiResponse<ProjectPhase> createPhase(@RequestBody Map<String, Object> body) {
        Object name = body == null ? null : body.get("name");
        Object sort = body == null ? null : body.get("sort");
        return ApiResponse.ok(projectService.createPhase(name == null ? null : String.valueOf(name),
                sort == null ? null : Integer.valueOf(String.valueOf(sort))));
    }

    @PutMapping("/config/phases/{id}")
    public ApiResponse<ProjectPhase> updatePhase(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object name = body == null ? null : body.get("name");
        Object sort = body == null ? null : body.get("sort");
        Object isActive = body == null ? null : body.get("isActive");
        return ApiResponse.ok(projectService.updatePhase(id, name == null ? null : String.valueOf(name),
                sort == null ? null : Integer.valueOf(String.valueOf(sort)),
                isActive == null ? null : Integer.valueOf(String.valueOf(isActive))));
    }

    @DeleteMapping("/config/phases/{id}")
    public ApiResponse<Void> deletePhase(@PathVariable Long id) {
        projectService.deletePhase(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/config/templates")
    public ApiResponse<List<Map<String, Object>>> listTemplates() {
        return ApiResponse.ok(projectService.listTemplates());
    }

    @PostMapping("/config/templates")
    public ApiResponse<ProjectTemplate> createTemplate(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object name = body == null ? null : body.get("name");
        Object type = body == null ? null : body.get("templateType");
        return ApiResponse.ok(projectService.createTemplate(name == null ? null : String.valueOf(name),
                type == null ? null : String.valueOf(type), user));
    }

    @GetMapping("/config/phases/{phaseId}/templates")
    public ApiResponse<List<Map<String, Object>>> listPhaseTemplates(@PathVariable Long phaseId) {
        return ApiResponse.ok(projectService.listPhaseTemplates(phaseId));
    }

    @PutMapping("/config/phases/{phaseId}/templates")
    public ApiResponse<Void> savePhaseTemplates(@PathVariable Long phaseId, @RequestBody List<Map<String, Object>> binds, @AuthenticationPrincipal AuthUser user) {
        projectService.savePhaseTemplates(phaseId, binds, user);
        return ApiResponse.ok(null);
    }

    // ==================== 配置 v2（20261102）：类型流程 + 类型专属绑定 + 模板编辑删除 ====================

    /** 类型阶段序列（按 sort 升序）。 */
    @GetMapping("/config/types/{typeId}/phases")
    public ApiResponse<List<Map<String, Object>>> listTypePhases(@PathVariable Long typeId) {
        return ApiResponse.ok(projectService.listTypePhases(typeId));
    }

    /** 全量保存类型阶段序列（入参为排序后的 phaseId 数组）。 */
    @PutMapping("/config/types/{typeId}/phases")
    public ApiResponse<Void> saveTypePhases(@PathVariable Long typeId, @RequestBody List<Long> phaseIds, @AuthenticationPrincipal AuthUser user) {
        projectService.saveTypePhases(typeId, phaseIds, user);
        return ApiResponse.ok(null);
    }

    /** 类型专属阶段模板绑定（无专属时回退全局兜底，fallback=true 标识）。 */
    @GetMapping("/config/types/{typeId}/phases/{phaseId}/templates")
    public ApiResponse<List<Map<String, Object>>> listTypePhaseTemplates(@PathVariable Long typeId, @PathVariable Long phaseId) {
        return ApiResponse.ok(projectService.listTypePhaseTemplates(typeId, phaseId));
    }

    /** 全量保存类型专属阶段模板绑定（type_id=类型ID）。 */
    @PutMapping("/config/types/{typeId}/phases/{phaseId}/templates")
    public ApiResponse<Void> saveTypePhaseTemplates(@PathVariable Long typeId, @PathVariable Long phaseId,
                                                    @RequestBody List<Map<String, Object>> binds, @AuthenticationPrincipal AuthUser user) {
        projectService.saveTypePhaseTemplates(typeId, phaseId, binds, user);
        return ApiResponse.ok(null);
    }

    /** 修改模板（名称/模板类型）。 */
    @PutMapping("/config/templates/{id}")
    public ApiResponse<ProjectTemplate> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object name = body == null ? null : body.get("name");
        Object type = body == null ? null : body.get("templateType");
        return ApiResponse.ok(projectService.updateTemplate(id, name == null ? null : String.valueOf(name),
                type == null ? null : String.valueOf(type), user));
    }

    /** 删除模板（被绑定引用时拦截）。 */
    @DeleteMapping("/config/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        projectService.deleteTemplate(id, user);
        return ApiResponse.ok(null);
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
