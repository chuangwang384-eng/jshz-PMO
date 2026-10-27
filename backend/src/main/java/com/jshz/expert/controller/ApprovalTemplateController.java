package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.entity.ApprovalTemplate;
import com.jshz.expert.service.ApplicationService;
import com.jshz.expert.service.ApprovalTemplateService;
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批申请字段模板接口（需求 R-B4）。
 * - GET  /api/approval/templates       启用模板列表（登录即可，员工提交申请下拉）；
 * - GET  /api/approval/templates/all  全部模板含停用（权限码 approval:config）；
 * - GET  /api/approval/template-fields 字段清单（登录即可，管理页多选选项）；
 * - POST /api/approval/templates       新增模板（权限码 approval:config）；
 * - PUT  /api/approval/templates/{id}  修改/启停模板（权限码 approval:config）；
 * - DELETE /api/approval/templates/{id} 删除模板（权限码 approval:config）。
 * 管理端接口权限由 permission 表 method+path 规则控制（见 migration 20261026）。
 */
@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalTemplateController {

    private final ApprovalTemplateService approvalTemplateService;

    /**
     * 启用模板列表：GET /api/approval/templates（登录即可）。
     */
    @GetMapping("/templates")
    public ApiResponse<List<ApprovalTemplate>> listEnabled() {
        return ApiResponse.ok(approvalTemplateService.listEnabled());
    }

    /**
     * 全部模板（含停用）：GET /api/approval/templates/all（仅管理员，权限码 approval:config）。
     */
    @GetMapping("/templates/all")
    public ApiResponse<List<ApprovalTemplate>> listAll() {
        return ApiResponse.ok(approvalTemplateService.listAll());
    }

    /**
     * 可选字段清单：GET /api/approval/template-fields（登录即可）。
     * 返回 {textFields, photoFields, createRequiredFields}，管理页多选选项与必填提示用。
     */
    @GetMapping("/template-fields")
    public ApiResponse<Map<String, Object>> fieldCatalog() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("textFields", ApplicationService.WHITELIST_FIELDS);
        data.put("photoFields", ApplicationService.PHOTO_FIELDS);
        data.put("createRequiredFields", ApplicationService.CREATE_REQUIRED_FIELDS);
        return ApiResponse.ok(data);
    }

    /**
     * 新增模板：POST /api/approval/templates，body {templateName, fields:[中文键], enabled}。
     */
    @PostMapping("/templates")
    public ApiResponse<ApprovalTemplate> create(@RequestBody(required = false) Map<String, Object> body) {
        requireBody(body);
        ApprovalTemplate template = approvalTemplateService.create(
                (String) body.get("templateName"), toStringList(body.get("fields")),
                ParamUtils.toInteger(body.get("enabled")));
        return ApiResponse.ok(template);
    }

    /**
     * 修改/启停模板：PUT /api/approval/templates/{id}，body {templateName, fields, enabled}。
     */
    @PutMapping("/templates/{id}")
    public ApiResponse<ApprovalTemplate> update(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        requireBody(body);
        ApprovalTemplate template = approvalTemplateService.update(
                id, (String) body.get("templateName"), toStringList(body.get("fields")),
                ParamUtils.toInteger(body.get("enabled")));
        return ApiResponse.ok(template);
    }

    /**
     * 删除模板：DELETE /api/approval/templates/{id}（历史申请单 template_id 保留为来源记录）。
     */
    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        approvalTemplateService.delete(id);
        return ApiResponse.ok(null);
    }

    private void requireBody(Map<String, Object> body) {
        if (body == null) {
            throw new com.jshz.expert.common.BusinessException("请求体不能为空");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return null;
    }

}
