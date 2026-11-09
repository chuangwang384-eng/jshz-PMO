package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.annotation.OperationLog;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierCity;
import com.jshz.expert.entity.SupplierImage;
import com.jshz.expert.entity.SupplierPriceItem;
import com.jshz.expert.entity.SupplierReview;
import com.jshz.expert.entity.SupplierType;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.SupplierService;
import com.jshz.expert.service.SupplierImageService;
import com.jshz.expert.service.SupplierStorageService;
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商管理接口（替换前端占位页，全员使用）。
 * 权限：档案/行价/评价/绑定/图片 登录即可；类型字典管理需 supplier:dict；城市字典固定只读（增删接口已下线返回 410）。
 */
@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    /** 城市字典固定只读业务码 */
    private static final String CITY_READ_ONLY = "CITY_READ_ONLY";

    private final SupplierService supplierService;
    private final SupplierStorageService storageService;
    private final SupplierImageService imageService;

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

    // ==================== 城市字典（固定只读） ====================

    /**
     * 城市列表（登录即可；前端按 province 分组展示）。
     * province 可选：按省份过滤，支持省→市两级联动。
     */
    @GetMapping("/cities")
    public ApiResponse<List<SupplierCity>> listCities(@RequestParam(required = false) String province) {
        return ApiResponse.ok(supplierService.listCities(province));
    }

    /**
     * 城市字典固定只读：新增接口已下线，统一返回 HTTP 410 + 业务码 CITY_READ_ONLY。
     */
    @PostMapping("/cities")
    public ResponseEntity<ApiResponse<Void>> createCity() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error(410, CITY_READ_ONLY + "：城市字典固定只读，禁止新增"));
    }

    /**
     * 城市字典固定只读：删除接口已下线，统一返回 HTTP 410 + 业务码 CITY_READ_ONLY。
     */
    @DeleteMapping("/cities/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCity(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error(410, CITY_READ_ONLY + "：城市字典固定只读，禁止删除"));
    }

    // ==================== 供应商档案 ====================

    /**
     * 分页列表：GET /api/supplier/list?keyword=&typeId=&cityId=&onlyReviewed=&sort=&page=&size=（登录即可）。
     * onlyReviewed=true 只返回已有评价的供应商；sort=name（默认，按名称升序）/ updated（按更新时间倒序）。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<Supplier>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long typeId,
                                                  @RequestParam(required = false) Long cityId,
                                                  @RequestParam(required = false, defaultValue = "false") boolean onlyReviewed,
                                                  @RequestParam(required = false, defaultValue = "name") String sort,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "12") long size) {
        Page<Supplier> p = supplierService.pageSuppliers(keyword, typeId, cityId, onlyReviewed, sort, page, size);
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
     * 传 cityName 时仅精确匹配字典，未收录返回明确错误（城市字典固定只读，不自动沉淀）。
     */
    @OperationLog(actionType = "SUPPLIER_CREATE", actionDesc = "新增供应商：#{#body.name}",
            targetType = "SUPPLIER", targetId = "")
    @PostMapping
    public ApiResponse<Supplier> create(@RequestBody(required = false) Map<String, Object> body,
                                        @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(supplierService.createSupplier(body, authUser));
    }

    /**
     * 修改供应商（全员）。
     */
    @OperationLog(actionType = "SUPPLIER_UPDATE", actionDesc = "修改供应商：#{#body.name}",
            targetType = "SUPPLIER", targetId = "#id")
    @PutMapping("/{id}")
    public ApiResponse<Supplier> update(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(supplierService.updateSupplier(id, body));
    }

    /**
     * 删除供应商（全员）：级联删除行价/评价/绑定与图片文件。
     */
    @OperationLog(actionType = "SUPPLIER_DELETE", actionDesc = "删除供应商：ID=#{#id}",
            targetType = "SUPPLIER", targetId = "#id")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthUser authUser) {
        supplierService.deleteSupplier(id);
        return ApiResponse.ok();
    }

    // ==================== 图片与主图 ====================

    /**
     * 上传图片（v2.0）：POST /api/supplier/{id}/images（multipart file + 可选 name）。
     * 校验：供应商存在、数量 ≤20、单张 ≤8MB、累计 ≤100MB、扩展名/MIME 白名单；
     * 文件重命名为 UUID；首图自动设为默认主图；name 自定义命名 ≤50 字符（留空显示原文件名）。
     */
    @PostMapping("/{id}/images")
    public ApiResponse<SupplierImage> uploadImage(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "name", required = false) String name) {
        return ApiResponse.ok(imageService.upload(id, file, name));
    }

    /**
     * 图片列表（返回主图标记 isMain，按 sort_order 升序）：GET /api/supplier/{id}/images。
     */
    @GetMapping("/{id}/images")
    public ApiResponse<List<SupplierImage>> listImages(@PathVariable Long id) {
        return ApiResponse.ok(imageService.list(id));
    }

    /**
     * 设为主图：PUT /api/supplier/{id}/images/{imageId}/main。
     * 同一事务先清旧主图再设新主图，配合部分唯一索引防并发多主图。
     */
    @PutMapping("/{id}/images/{imageId}/main")
    public ApiResponse<SupplierImage> setMainImage(@PathVariable Long id, @PathVariable Long imageId) {
        return ApiResponse.ok(imageService.setMain(id, imageId));
    }

    /**
     * 图片改名（v2.1）：PUT /api/supplier/{id}/images/{imageId}/name。body {name}。
     * name ≤50 字符，允许为空与重名；仅修改展示名，物理文件不变。
     * 错误码：IMAGE_NOT_FOUND（404）/ NAME_TOO_LONG（400）。
     */
    @PutMapping("/{id}/images/{imageId}/name")
    public ApiResponse<SupplierImage> renameImage(@PathVariable Long id, @PathVariable Long imageId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        String name = ParamUtils.str(body == null ? null : body.get("name"));
        return ApiResponse.ok(imageService.updateName(id, imageId, name));
    }

    /**
     * 删除图片：DELETE /api/supplier/{id}/images/{imageId}。
     * 同步删除物理文件与数据库行；删除主图时自动提升排序最小的一张为新主图。
     */
    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<List<SupplierImage>> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return ApiResponse.ok(imageService.delete(id, imageId));
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
    @OperationLog(actionType = "SAVE_SUPPLIER_PRICE", actionDesc = "保存行价：供应商ID=#{#id}",
            targetType = "SUPPLIER", targetId = "#id")
    @PutMapping("/{id}/prices")
    public ApiResponse<List<SupplierPriceItem>> savePrices(@PathVariable Long id,
                                                           @RequestBody(required = false) Map<String, Object> body,
                                                           @AuthenticationPrincipal AuthUser authUser) {
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
    @OperationLog(actionType = "BIND_SUPPLIER", actionDesc = "绑定供应商：ID=#{#id} 绑定至 #{#body.targetId}",
            targetType = "SUPPLIER", targetId = "#id")
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
    @OperationLog(actionType = "UNBIND_SUPPLIER", actionDesc = "解绑供应商：ID=#{#id} 解绑 #{#targetId}",
            targetType = "SUPPLIER", targetId = "#id")
    @DeleteMapping("/{id}/bindings/{targetId}")
    public ApiResponse<Void> unbind(@PathVariable Long id, @PathVariable Long targetId,
                                    @AuthenticationPrincipal AuthUser authUser) {
        supplierService.unbind(id, targetId);
        return ApiResponse.ok();
    }

    /**
     * 跨城绑定清单（v2.1）：GET /api/supplier/bindings/cross-city?keyword=&page=&size=。
     * 返回存量绑定中酒店与打印店城市不一致的关系（分页 {total, items}）。
     */
    @GetMapping("/bindings/cross-city")
    public ApiResponse<PageResult<Map<String, Object>>> crossCityBindings(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "12") long size) {
        Page<Map<String, Object>> p = supplierService.pageCrossCityBindings(keyword, page, size);
        return ApiResponse.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 批量解绑（v2.1）：POST /api/supplier/bindings/unbind-batch。body {relIds:[]}。
     * 事务内统一校验并删除；relIds 为空返回 PARAM_INVALID（400），
     * 存在不存在的关系整体失败返回 BINDING_NOT_FOUND（404）；成功返回 {unboundCount}。
     */
    @PostMapping("/bindings/unbind-batch")
    public ApiResponse<Map<String, Object>> unbindBatch(@RequestBody(required = false) Map<String, Object> body) {
        List<Long> relIds = new ArrayList<>();
        Object raw = body == null ? null : body.get("relIds");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                Long v = ParamUtils.toLong(o == null ? null : o.toString());
                if (v != null) {
                    relIds.add(v);
                }
            }
        }
        int count = supplierService.unbindBatch(relIds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unboundCount", count);
        return ApiResponse.ok(result);
    }

    // ==================== 私有 ====================



}
