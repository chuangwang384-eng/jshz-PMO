package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierCity;
import com.jshz.expert.entity.SupplierPriceItem;
import com.jshz.expert.entity.SupplierReview;
import com.jshz.expert.entity.SupplierType;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.SupplierService;
import com.jshz.expert.service.SupplierStorageService;
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
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

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商管理接口（替换前端占位页，全员使用）。
 * 权限：档案/行价/评价/绑定/图片 登录即可；类型/城市字典管理需 supplier:dict（permission 表路径规则）。
 */
@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierStorageService storageService;

    // ==================== 类型字典 ====================

    @GetMapping("/types")
    public ApiResponse<List<SupplierType>> listTypes() {
        return ApiResponse.ok(supplierService.listTypes());
    }

    /** 管理端：新增类型（supplier:dict）。body {name, sort?} */
    @PostMapping("/types")
    public ApiResponse<SupplierType> createType(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(supplierService.createType(ParamUtils.str(body == null ? null : body.get("name")),
                ParamUtils.toInteger(body == null ? null : body.get("sort"))));
    }

    /** 管理端：修改类型名称/排序（supplier:dict）。body {name, sort?} */
    @PutMapping("/types/{id}")
    public ApiResponse<SupplierType> updateType(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(supplierService.updateType(id,
                ParamUtils.str(body == null ? null : body.get("name")),
                ParamUtils.toInteger(body == null ? null : body.get("sort"))));
    }

    /** 管理端：删除类型（有供应商引用时拒绝；supplier:dict） */
    @DeleteMapping("/types/{id}")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        supplierService.deleteType(id);
        return ApiResponse.ok();
    }

    // ==================== 城市字典 ====================

    /** 全部城市（登录即可；前端按 province 分组，城市管理页同一数据源） */
    @GetMapping("/cities")
    public ApiResponse<List<SupplierCity>> listCities() {
        return ApiResponse.ok(supplierService.listCities());
    }

    /** 管理端：新增城市（supplier:dict）。body {province, name} */
    @PostMapping("/cities")
    public ApiResponse<SupplierCity> createCity(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(supplierService.createCity(
                ParamUtils.str(body == null ? null : body.get("province")),
                ParamUtils.str(body == null ? null : body.get("name"))));
    }

    /** 管理端：删除城市（有供应商引用时拒绝；supplier:dict） */
    @DeleteMapping("/cities/{id}")
    public ApiResponse<Void> deleteCity(@PathVariable Long id) {
        supplierService.deleteCity(id);
        return ApiResponse.ok();
    }

    // ==================== 供应商档案 ====================

    /**
     * 分页列表：GET /api/supplier/list?keyword=&typeId=&cityId=&page=&size=（登录即可）。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<Supplier>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long typeId,
                                                  @RequestParam(required = false) Long cityId,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "12") long size) {
        Page<Supplier> p = supplierService.pageSuppliers(keyword, typeId, cityId, page, size);
        return ApiResponse.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 详情：GET /api/supplier/{id}。返回 {supplier, priceItems, reviews, bindings}。
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(supplierService.detail(id));
    }

    /**
     * 新增供应商（全员）：body {name, typeId, cityId 或 cityName, address, contact, phone, wechat, description, images:[文件名]}。
     * 传 cityName 且字典未收录时自动沉淀为新城市。
     */
    @PostMapping
    public ApiResponse<Supplier> create(@RequestBody(required = false) Map<String, Object> body,
                                        @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(supplierService.createSupplier(body, authUser));
    }

    /**
     * 修改供应商（全员）。
     */
    @PutMapping("/{id}")
    public ApiResponse<Supplier> update(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(supplierService.updateSupplier(id, body));
    }

    /**
     * 删除供应商（全员）：级联删除行价/评价/绑定与图片文件。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ApiResponse.ok();
    }

    // ==================== 图片 ====================

    /**
     * 上传单张图片（前端 canvas 压缩后 multipart 上传，可多次调用）：POST /api/supplier/image。
     * 返回 {fileName}，保存供应商时以 images 数组引用。
     */
    @PostMapping("/image")
    public ApiResponse<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        String fileName = storageService.saveImage(file);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileName", fileName);
        return ApiResponse.ok(data);
    }

    /**
     * 图片流（inline 展示）：GET /api/supplier/images/{fileName}（登录即可）。
     */
    @GetMapping("/images/{fileName}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String fileName) {
        Path path = storageService.resolveImage(fileName);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storageService.contentTypeOf(fileName)))
                .body(new FileSystemResource(path));
    }

    // ==================== 行价 ====================

    /**
     * 保存行价（全量替换，全员）：PUT /api/supplier/{id}/prices。
     * body {items: [{itemName, price, unit, remark, sort?}]}。
     */
    @PutMapping("/{id}/prices")
    public ApiResponse<List<SupplierPriceItem>> savePrices(@PathVariable Long id,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = body == null ? null : (List<Map<String, Object>>) body.get("items");
        return ApiResponse.ok(supplierService.savePrices(id, items));
    }

    // ==================== 评价 ====================

    /**
     * 提交评价（即发即显，全员）：POST /api/supplier/{id}/reviews。body {stars, content}。
     */
    @PostMapping("/{id}/reviews")
    public ApiResponse<SupplierReview> addReview(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 @AuthenticationPrincipal AuthUser authUser) {
        Integer stars = ParamUtils.toInteger(body == null ? null : body.get("stars"));
        String content = ParamUtils.str(body == null ? null : body.get("content"));
        return ApiResponse.ok(supplierService.addReview(id, stars, content, authUser));
    }

    // ==================== 酒店-打印店绑定 ====================

    /**
     * 建立绑定（多对多，酒店或打印店任意一端发起）：POST /api/supplier/{id}/bindings。
     * body {targetId}；后端按类型自动定向并校验（仅酒店↔打印店）。
     */
    @PostMapping("/{id}/bindings")
    public ApiResponse<Void> bind(@PathVariable Long id,
                                  @RequestBody(required = false) Map<String, Object> body,
                                  @AuthenticationPrincipal AuthUser authUser) {
        Long targetId = ParamUtils.toLong(body == null ? null : body.get("targetId"));
        if (targetId == null) {
            throw new BusinessException("目标供应商不能为空");
        }
        supplierService.bind(id, targetId, authUser);
        return ApiResponse.ok();
    }

    /**
     * 解绑（双向可操作）：DELETE /api/supplier/{id}/bindings/{targetId}。
     */
    @DeleteMapping("/{id}/bindings/{targetId}")
    public ApiResponse<Void> unbind(@PathVariable Long id, @PathVariable Long targetId) {
        supplierService.unbind(id, targetId);
        return ApiResponse.ok();
    }

    // ==================== 私有 ====================



}
