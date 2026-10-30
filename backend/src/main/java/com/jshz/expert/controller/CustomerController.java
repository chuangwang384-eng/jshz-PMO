package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.dto.CustomerForm;
import com.jshz.expert.dto.CustomerRelationForm;
import com.jshz.expert.dto.CustomerRelationTypeForm;
import com.jshz.expert.entity.Customer;
import com.jshz.expert.entity.CustomerAttachment;
import com.jshz.expert.entity.CustomerRelation;
import com.jshz.expert.entity.CustomerRelationType;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.CustomerAttachmentStorageService;
import com.jshz.expert.service.CustomerRelationService;
import com.jshz.expert.service.CustomerService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 客户信息模块接口（客户档案管理 + 客户关系网络）。
 * 查看类接口登录即可；管理类接口（新增/编辑/软删除/恢复/附件管理/关系维护/关系类型配置）
 * 由权限码 customer:manage 控制（过滤器 + 业务层双重校验），仅 ADMIN 与知识星球维护人（KNOWLEDGE_MAINTAINER）持有。
 * 客户模块独立管理，不涉及会议执行/协会合规/企业合规三个子板块。
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerRelationService relationService;
    private final CustomerAttachmentStorageService attachmentStorageService;
    private final OperationLogService operationLogService;

    // ==================== 客户档案 ====================

    /**
     * 新增客户档案：POST /api/customer（body CustomerForm）。需 customer:manage。
     */
    @PostMapping
    public ApiResponse<Customer> create(@RequestBody CustomerForm form,
                                        @AuthenticationPrincipal AuthUser authUser,
                                        HttpServletRequest request) {
        requireManage(authUser);
        Customer customer = customerService.create(form, authUser);
        operationLogService.record(userId(authUser), username(authUser), "CREATE_CUSTOMER",
                "新增客户档案《" + customer.getName() + "》", "CUSTOMER", customer.getId(), request);
        return ApiResponse.ok(customer);
    }

    /**
     * 分页检索：GET /api/customer?keyword=&deleted=&page=&pageSize=。登录即可。
     * deleted 可选：0=正常（默认）；1=已删除（仅维护人可查，用于恢复）；不传=全部。
     */
    @GetMapping
    public ApiResponse<PageResult<Customer>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer deleted,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long pageSize,
                                                  @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        if (deleted != null && deleted == 1) {
            requireManage(authUser);
        }
        Page<Customer> p = customerService.pageList(keyword, deleted, page, pageSize);
        return ApiResponse.ok(PageResult.of(p));
    }

    /**
     * 客户档案详情：GET /api/customer/{id}。登录即可。
     */
    @GetMapping("/{id}")
    public ApiResponse<Customer> detail(@PathVariable Long id,
                                        @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        return ApiResponse.ok(customerService.getVisible(id));
    }

    /**
     * 编辑客户档案：PUT /api/customer/{id}。需 customer:manage。
     */
    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable Long id,
                                        @RequestBody CustomerForm form,
                                        @AuthenticationPrincipal AuthUser authUser,
                                        HttpServletRequest request) {
        requireManage(authUser);
        Customer customer = customerService.update(id, form, authUser);
        operationLogService.record(userId(authUser), username(authUser), "UPDATE_CUSTOMER",
                "编辑客户档案《" + customer.getName() + "》", "CUSTOMER", id, request);
        return ApiResponse.ok(customer);
    }

    /**
     * 软删除客户档案：DELETE /api/customer/{id}（默认隐藏，可恢复）。需 customer:manage。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable Long id,
                                        @AuthenticationPrincipal AuthUser authUser,
                                        HttpServletRequest request) {
        requireManage(authUser);
        Customer before = customerService.getVisible(id);
        customerService.softDelete(id, authUser);
        operationLogService.record(userId(authUser), username(authUser), "DELETE_CUSTOMER",
                "删除客户档案《" + before.getName() + "》", "CUSTOMER", id, request);
        return ApiResponse.ok();
    }

    /**
     * 恢复已删除客户档案：POST /api/customer/{id}/restore。需 customer:manage。
     */
    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id,
                                     @AuthenticationPrincipal AuthUser authUser,
                                     HttpServletRequest request) {
        requireManage(authUser);
        customerService.restore(id, authUser);
        operationLogService.record(userId(authUser), username(authUser), "RESTORE_CUSTOMER",
                "恢复客户档案（id=" + id + "）", "CUSTOMER", id, request);
        return ApiResponse.ok();
    }

    // ==================== 附件管理 ====================

    /**
     * 上传附件：POST /api/customer/{id}/attachments（multipart file）。需 customer:manage。
     */
    @PostMapping("/{id}/attachments")
    public ApiResponse<CustomerAttachment> uploadAttachment(@PathVariable Long id,
                                                            @RequestParam("file") MultipartFile file,
                                                            @AuthenticationPrincipal AuthUser authUser,
                                                            HttpServletRequest request) {
        requireManage(authUser);
        CustomerAttachment attachment = customerService.addAttachment(id, file, authUser);
        operationLogService.record(userId(authUser), username(authUser), "UPLOAD_ATTACH",
                "客户档案（id=" + id + "）上传附件《" + attachment.getOriginalName() + "》", "CUSTOMER", id, request);
        return ApiResponse.ok(attachment);
    }

    /**
     * 附件列表：GET /api/customer/{id}/attachments。登录即可。
     */
    @GetMapping("/{id}/attachments")
    public ApiResponse<List<CustomerAttachment>> attachments(@PathVariable Long id,
                                                             @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        return ApiResponse.ok(customerService.listAttachments(id));
    }

    /**
     * 删除附件：DELETE /api/customer/attachments/{attachmentId}。需 customer:manage。
     */
    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long attachmentId,
                                              @AuthenticationPrincipal AuthUser authUser,
                                              HttpServletRequest request) {
        requireManage(authUser);
        customerService.deleteAttachment(attachmentId, authUser);
        operationLogService.record(userId(authUser), username(authUser), "DELETE_ATTACH",
                "删除客户附件（attachmentId=" + attachmentId + "）", "CUSTOMER", attachmentId, request);
        return ApiResponse.ok();
    }

    /**
     * 附件下载：GET /api/customer/files/{fileName}（默认下载）。登录即可。
     */
    @GetMapping("/files/{fileName}")
    public ResponseEntity<FileSystemResource> file(@PathVariable String fileName,
                                                   @AuthenticationPrincipal AuthUser authUser) throws IOException {
        requireUser(authUser);
        Path path = attachmentStorageService.resolveAttachment(fileName);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        String safeName = path.getFileName().toString();
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "attachment; filename=\"" + safeName.replace("\"", "") + "\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    // ==================== 客户关系 ====================

    /**
     * 关系类型列表：GET /api/customer/relations/types（含停用类型）。登录即可。
     */
    @GetMapping("/relations/types")
    public ApiResponse<List<CustomerRelationType>> relationTypes(@AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        return ApiResponse.ok(relationService.listTypes());
    }

    /**
     * 新增关系类型：POST /api/customer/relations/types。需 customer:manage。
     */
    @PostMapping("/relations/types")
    public ApiResponse<CustomerRelationType> createRelationType(@RequestBody CustomerRelationTypeForm form,
                                                                @AuthenticationPrincipal AuthUser authUser,
                                                                HttpServletRequest request) {
        requireManage(authUser);
        CustomerRelationType type = relationService.createType(form, authUser);
        operationLogService.record(userId(authUser), username(authUser), "CREATE_RELATION_TYPE",
                "新增客户关系类型《" + type.getName() + "》", "CUSTOMER", type.getId(), request);
        return ApiResponse.ok(type);
    }

    /**
     * 编辑关系类型：PUT /api/customer/relations/types/{id}（改名称/停用启用）。需 customer:manage。
     */
    @PutMapping("/relations/types/{id}")
    public ApiResponse<CustomerRelationType> updateRelationType(@PathVariable Long id,
                                                                @RequestBody CustomerRelationTypeForm form,
                                                                @AuthenticationPrincipal AuthUser authUser,
                                                                HttpServletRequest request) {
        requireManage(authUser);
        CustomerRelationType type = relationService.updateType(id, form, authUser);
        operationLogService.record(userId(authUser), username(authUser), "UPDATE_RELATION_TYPE",
                "编辑客户关系类型（id=" + id + "）", "CUSTOMER", id, request);
        return ApiResponse.ok(type);
    }

    /**
     * 以某客户为中心的关系圈脑图：GET /api/customer/{id}/relations。登录即可。
     */
    @GetMapping("/{id}/relations")
    public ApiResponse<Map<String, Object>> centerGraph(@PathVariable Long id,
                                                        @AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        return ApiResponse.ok(relationService.centerGraph(id));
    }

    /**
     * 全局关系网络脑图：GET /api/customer/relations/network。登录即可。
     */
    @GetMapping("/relations/network")
    public ApiResponse<Map<String, Object>> networkGraph(@AuthenticationPrincipal AuthUser authUser) {
        requireUser(authUser);
        return ApiResponse.ok(relationService.networkGraph());
    }

    /**
     * 建立关系：POST /api/customer/relations（body CustomerRelationForm，有向 from->to）。需 customer:manage。
     */
    @PostMapping("/relations")
    public ApiResponse<CustomerRelation> createRelation(@RequestBody CustomerRelationForm form,
                                                        @AuthenticationPrincipal AuthUser authUser,
                                                        HttpServletRequest request) {
        requireManage(authUser);
        CustomerRelation relation = relationService.createRelation(form, authUser);
        operationLogService.record(userId(authUser), username(authUser), "CREATE_RELATION",
                "建立客户关系（" + form.getFromCustomerId() + " -> " + form.getToCustomerId() + "）",
                "CUSTOMER", relation.getId(), request);
        return ApiResponse.ok(relation);
    }

    /**
     * 删除关系：DELETE /api/customer/relations/{id}。需 customer:manage。
     */
    @DeleteMapping("/relations/{id}")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id,
                                            @AuthenticationPrincipal AuthUser authUser,
                                            HttpServletRequest request) {
        requireManage(authUser);
        relationService.deleteRelation(id, authUser);
        operationLogService.record(userId(authUser), username(authUser), "DELETE_RELATION",
                "删除客户关系（relationId=" + id + "）", "CUSTOMER", id, request);
        return ApiResponse.ok();
    }

    // ==================== 权限校验 ====================

    private void requireUser(AuthUser authUser) {
        if (authUser == null) {
            throw new BusinessException("未登录");
        }
    }

    /** 客户模块管理权限校验（customer:manage；SuperAdmin 由 PermissionService 强制全权）。 */
    private void requireManage(AuthUser authUser) {
        requireUser(authUser);
        if (!authUser.hasPermission("customer:manage")) {
            throw new BusinessException("无权限执行该操作");
        }
    }

    private Long userId(AuthUser authUser) {
        return authUser == null ? null : authUser.getUserId();
    }

    private String username(AuthUser authUser) {
        return authUser == null ? null : authUser.getUsername();
    }
}
