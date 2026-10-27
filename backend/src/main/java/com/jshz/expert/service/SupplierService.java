package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierCity;
import com.jshz.expert.entity.SupplierHotelPrintRel;
import com.jshz.expert.entity.SupplierPriceItem;
import com.jshz.expert.entity.SupplierReview;
import com.jshz.expert.entity.SupplierType;
import com.jshz.expert.mapper.SupplierCityMapper;
import com.jshz.expert.mapper.SupplierHotelPrintRelMapper;
import com.jshz.expert.mapper.SupplierMapper;
import com.jshz.expert.mapper.SupplierPriceItemMapper;
import com.jshz.expert.mapper.SupplierReviewMapper;
import com.jshz.expert.mapper.SupplierTypeMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商管理服务：档案（全员维护）、类型/城市字典、行价、评价、酒店-打印店多对多绑定。
 * 权限：档案/行价/评价/绑定 登录即可；类型/城市管理由接口层 supplier:dict 权限码控制。
 */
@Service
@RequiredArgsConstructor
public class SupplierService extends ServiceImpl<SupplierMapper, Supplier> {

    /** 酒店类型业务键 */
    public static final String BIZ_HOTEL = "HOTEL";

    /** 打印店类型业务键 */
    public static final String BIZ_PRINT = "PRINT";

    /** 城市自动沉淀时的省份占位 */
    private static final String PROVINCE_OTHER = "其他";

    private final SupplierTypeMapper typeMapper;
    private final SupplierCityMapper cityMapper;
    private final SupplierPriceItemMapper priceItemMapper;
    private final SupplierReviewMapper reviewMapper;
    private final SupplierHotelPrintRelMapper relMapper;
    private final SupplierStorageService storageService;
    private final ObjectMapper objectMapper;

    // ==================== 类型字典 ====================

    public List<SupplierType> listTypes() {
        return typeMapper.selectList(new LambdaQueryWrapper<SupplierType>()
                .orderByAsc(SupplierType::getSort).orderByAsc(SupplierType::getId));
    }

    public SupplierType createType(String name, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("类型名称不能为空");
        }
        String trimmed = name.trim();
        if (typeMapper.selectCount(new LambdaQueryWrapper<SupplierType>().eq(SupplierType::getName, trimmed)) > 0) {
            throw new BusinessException("类型名称已存在");
        }
        SupplierType t = new SupplierType();
        t.setName(trimmed);
        t.setSort(sort == null ? 0 : sort);
        typeMapper.insert(t);
        return t;
    }

    public SupplierType updateType(Long id, String name, Integer sort) {
        SupplierType t = typeMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("类型不存在");
        }
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("类型名称不能为空");
        }
        String trimmed = name.trim();
        Long dup = typeMapper.selectCount(new LambdaQueryWrapper<SupplierType>()
                .eq(SupplierType::getName, trimmed).ne(SupplierType::getId, id));
        if (dup != null && dup > 0) {
            throw new BusinessException("类型名称已存在");
        }
        t.setName(trimmed);
        if (sort != null) {
            t.setSort(sort);
        }
        typeMapper.updateById(t);
        return t;
    }

    /**
     * 删除类型：有供应商引用时禁止删除。
     */
    public void deleteType(Long id) {
        if (id == null || typeMapper.selectById(id) == null) {
            throw new BusinessException("类型不存在");
        }
        Long used = this.count(new LambdaQueryWrapper<Supplier>().eq(Supplier::getTypeId, id));
        if (used != null && used > 0) {
            throw new BusinessException("该类型下仍有 " + used + " 家供应商，不能删除");
        }
        typeMapper.deleteById(id);
    }

    // ==================== 城市字典 ====================

    /**
     * 全部城市（按 id 升序保持预置省份分组顺序；前端按 province 分组展示）。
     */
    public List<SupplierCity> listCities() {
        return cityMapper.selectList(new LambdaQueryWrapper<SupplierCity>().orderByAsc(SupplierCity::getId));
    }

    public SupplierCity createCity(String province, String name) {
        if (!StringUtils.hasText(province) || !StringUtils.hasText(name)) {
            throw new BusinessException("省份与城市名不能为空");
        }
        String p = province.trim();
        String n = name.trim();
        Long dup = cityMapper.selectCount(new LambdaQueryWrapper<SupplierCity>()
                .eq(SupplierCity::getProvince, p).eq(SupplierCity::getName, n));
        if (dup != null && dup > 0) {
            throw new BusinessException("该省份下城市已存在");
        }
        SupplierCity c = new SupplierCity();
        c.setProvince(p);
        c.setName(n);
        cityMapper.insert(c);
        return c;
    }

    /**
     * 删除城市：有供应商引用时禁止删除。
     */
    public void deleteCity(Long id) {
        if (id == null || cityMapper.selectById(id) == null) {
            throw new BusinessException("城市不存在");
        }
        Long used = this.count(new LambdaQueryWrapper<Supplier>().eq(Supplier::getCityId, id));
        if (used != null && used > 0) {
            throw new BusinessException("该城市下仍有 " + used + " 家供应商，不能删除");
        }
        cityMapper.deleteById(id);
    }

    /**
     * 按城市名解析城市 id：字典未收录时自动沉淀（province=其他）后返回新 id。
     */
    public Long resolveCityIdByName(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            throw new BusinessException("城市不能为空");
        }
        String name = cityName.trim();
        SupplierCity exist = cityMapper.selectOne(
                new LambdaQueryWrapper<SupplierCity>().eq(SupplierCity::getName, name).last("LIMIT 1"));
        if (exist != null) {
            return exist.getId();
        }
        SupplierCity c = new SupplierCity();
        c.setProvince(PROVINCE_OTHER);
        c.setName(name);
        cityMapper.insert(c);
        return c.getId();
    }

    // ==================== 供应商档案 ====================

    /**
     * 分页列表：keyword（名称/地址/联系人模糊）+ 类型 + 城市组合筛选，附带类型/城市名与评分统计。
     */
    public Page<Supplier> pageSuppliers(String keyword, Long typeId, Long cityId, long page, long size) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Supplier::getName, kw)
                    .or().like(Supplier::getAddress, kw)
                    .or().like(Supplier::getContact, kw));
        }
        if (typeId != null) {
            wrapper.eq(Supplier::getTypeId, typeId);
        }
        if (cityId != null) {
            wrapper.eq(Supplier::getCityId, cityId);
        }
        wrapper.orderByDesc(Supplier::getUpdateTime).orderByDesc(Supplier::getId);
        Page<Supplier> result = this.page(new Page<>(page, size), wrapper);
        enrich(result.getRecords());
        return result;
    }

    /**
     * 详情：基础信息 + 图片列表 + 行价 + 评价 + 绑定关系（按类型方向返回，供双向跳转）。
     */
    public Map<String, Object> detail(Long id) {
        Supplier s = this.getById(id);
        if (s == null) {
            throw new BusinessException("供应商不存在");
        }
        enrich(List.of(s));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("supplier", s);
        data.put("priceItems", priceItemsOf(id));
        data.put("reviews", reviewsOf(id));
        data.put("bindings", bindingsOf(s));
        return data;
    }

    public Supplier createSupplier(Map<String, Object> body, AuthUser user) {
        Supplier s = new Supplier();
        applyBody(s, body, true);
        if (!StringUtils.hasText(s.getName())) {
            throw new BusinessException("供应商名称不能为空");
        }
        s.setCreateBy(user == null ? null : user.getUsername());
        s.setCreateTime(LocalDateTime.now());
        this.save(s);
        return this.getById(s.getId());
    }

    public Supplier updateSupplier(Long id, Map<String, Object> body) {
        Supplier s = this.getById(id);
        if (s == null) {
            throw new BusinessException("供应商不存在");
        }
        List<String> oldImages = parseImages(s.getImages());
        // 部分更新语义：仅校验/应用传入字段（画廊增删图只传 images，不能强制要求类型/城市）
        applyBody(s, body, false);
        this.updateById(s);
        // 清理被移除的图片文件（保留仍引用的）
        List<String> newImages = parseImages(s.getImages());
        for (String img : oldImages) {
            if (!newImages.contains(img)) {
                storageService.deleteImage(img);
            }
        }
        return this.getById(id);
    }

    /**
     * 从请求体填充字段：name/typeId/cityId|cityName(自动沉淀)/address/contact/phone/wechat/description/images。
     * creating=true（新增）时 name/typeId/城市 必填；false（更新）时仅校验传入的字段，未传入保持原值。
     */
    private void applyBody(Supplier s, Map<String, Object> body, boolean creating) {
        if (body == null) {
            throw new BusinessException("请求体不能为空");
        }
        if (body.containsKey("name")) {
            String name = body.get("name") == null ? null : String.valueOf(body.get("name")).trim();
            if (!StringUtils.hasText(name)) {
                throw new BusinessException("供应商名称不能为空");
            }
            s.setName(name);
        } else if (creating) {
            throw new BusinessException("供应商名称不能为空");
        }
        if (body.containsKey("typeId")) {
            Long typeId = toLong(body.get("typeId"));
            if (typeId == null) {
                throw new BusinessException("供应商类型不能为空");
            }
            if (typeMapper.selectById(typeId) == null) {
                throw new BusinessException("供应商类型不存在");
            }
            s.setTypeId(typeId);
        } else if (creating) {
            throw new BusinessException("供应商类型不能为空");
        }
        boolean hasCityId = body.containsKey("cityId") && body.get("cityId") != null;
        boolean hasCityName = body.containsKey("cityName") && StringUtils.hasText(String.valueOf(body.get("cityName")));
        if (hasCityId || hasCityName) {
            if (hasCityId) {
                Long cityId = toLong(body.get("cityId"));
                if (cityId == null || cityMapper.selectById(cityId) == null) {
                    throw new BusinessException("城市不存在");
                }
                s.setCityId(cityId);
            } else {
                // 城市名：字典命中或自动沉淀
                s.setCityId(resolveCityIdByName(String.valueOf(body.get("cityName"))));
            }
        } else if (creating) {
            throw new BusinessException("城市不能为空");
        }
        if (body.containsKey("address")) {
            s.setAddress(str(body.get("address")));
        }
        if (body.containsKey("contact")) {
            s.setContact(str(body.get("contact")));
        }
        if (body.containsKey("phone")) {
            s.setPhone(str(body.get("phone")));
        }
        if (body.containsKey("wechat")) {
            s.setWechat(str(body.get("wechat")));
        }
        if (body.containsKey("description")) {
            s.setDescription(str(body.get("description")));
        }
        if (body.containsKey("images")) {
            Object imgs = body.get("images");
            List<String> list = new ArrayList<>();
            if (imgs instanceof List<?> raw) {
                for (Object o : raw) {
                    if (o != null && StringUtils.hasText(String.valueOf(o))) {
                        list.add(String.valueOf(o));
                    }
                }
            }
            s.setImages(writeJson(list));
        }
    }

    /**
     * 删除供应商：级联删除行价/评价/绑定关系与图片文件。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSupplier(Long id) {
        Supplier s = this.getById(id);
        if (s == null) {
            throw new BusinessException("供应商不存在");
        }
        priceItemMapper.delete(new LambdaQueryWrapper<SupplierPriceItem>().eq(SupplierPriceItem::getSupplierId, id));
        reviewMapper.delete(new LambdaQueryWrapper<SupplierReview>().eq(SupplierReview::getSupplierId, id));
        relMapper.delete(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                .eq(SupplierHotelPrintRel::getHotelId, id).or().eq(SupplierHotelPrintRel::getPrintShopId, id));
        this.removeById(id);
        for (String img : parseImages(s.getImages())) {
            storageService.deleteImage(img);
        }
    }

    // ==================== 行价 ====================

    public List<SupplierPriceItem> priceItemsOf(Long supplierId) {
        return priceItemMapper.selectList(new LambdaQueryWrapper<SupplierPriceItem>()
                .eq(SupplierPriceItem::getSupplierId, supplierId)
                .orderByAsc(SupplierPriceItem::getSort).orderByAsc(SupplierPriceItem::getId));
    }

    /**
     * 保存行价（全量替换）：[{itemName, price, unit, remark, sort}]。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SupplierPriceItem> savePrices(Long supplierId, List<Map<String, Object>> items) {
        Supplier s = this.getById(supplierId);
        if (s == null) {
            throw new BusinessException("供应商不存在");
        }
        priceItemMapper.delete(new LambdaQueryWrapper<SupplierPriceItem>().eq(SupplierPriceItem::getSupplierId, supplierId));
        int sort = 0;
        if (items != null) {
            for (Map<String, Object> item : items) {
                String itemName = str(item.get("itemName"));
                if (!StringUtils.hasText(itemName)) {
                    continue;
                }
                SupplierPriceItem p = new SupplierPriceItem();
                p.setSupplierId(supplierId);
                p.setItemName(itemName.trim());
                Object price = item.get("price");
                BigDecimal dec = null;
                try {
                    dec = price == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(price));
                } catch (NumberFormatException e) {
                    throw new BusinessException("行价「" + itemName + "」价格格式不正确");
                }
                if (dec.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("行价「" + itemName + "」价格不能为负");
                }
                p.setPrice(dec.setScale(2, RoundingMode.HALF_UP));
                p.setUnit(str(item.get("unit")));
                p.setRemark(str(item.get("remark")));
                Object st = item.get("sort");
                p.setSort(st instanceof Number n ? n.intValue() : sort);
                priceItemMapper.insert(p);
                sort++;
            }
        }
        return priceItemsOf(supplierId);
    }

    // ==================== 评价 ====================

    public List<SupplierReview> reviewsOf(Long supplierId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<SupplierReview>()
                .eq(SupplierReview::getSupplierId, supplierId)
                .orderByDesc(SupplierReview::getCreateTime).orderByDesc(SupplierReview::getId));
    }

    /**
     * 提交评价（即发即显）：1-5 星 + 文字，记录评价人姓名与时间。
     */
    public SupplierReview addReview(Long supplierId, Integer stars, String content, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        Supplier s = this.getById(supplierId);
        if (s == null) {
            throw new BusinessException("供应商不存在");
        }
        if (stars == null || stars < 1 || stars > 5) {
            throw new BusinessException("评分须为 1-5 星");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("评价内容不能为空");
        }
        SupplierReview r = new SupplierReview();
        r.setSupplierId(supplierId);
        r.setUsername(user.getUsername());
        r.setStars(stars);
        r.setContent(content.trim());
        r.setCreateTime(LocalDateTime.now());
        reviewMapper.insert(r);
        return r;
    }

    // ==================== 酒店-打印店绑定 ====================

    /**
     * 绑定关系（按供应商类型方向返回对端列表，含 id/name/cityName 供双向跳转）。
     */
    public List<Map<String, Object>> bindingsOf(Supplier s) {
        String biz = bizKeyOf(s.getTypeId());
        List<Long> targetIds;
        if (BIZ_HOTEL.equals(biz)) {
            targetIds = relMapper.selectList(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                            .eq(SupplierHotelPrintRel::getHotelId, s.getId()))
                    .stream().map(SupplierHotelPrintRel::getPrintShopId).collect(Collectors.toList());
        } else if (BIZ_PRINT.equals(biz)) {
            targetIds = relMapper.selectList(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                            .eq(SupplierHotelPrintRel::getPrintShopId, s.getId()))
                    .stream().map(SupplierHotelPrintRel::getHotelId).collect(Collectors.toList());
        } else {
            return List.of();
        }
        if (targetIds.isEmpty()) {
            return List.of();
        }
        List<Supplier> targets = this.listByIds(targetIds);
        enrich(targets);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Supplier t : targets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("cityName", t.getCityName());
            m.put("typeName", t.getTypeName());
            result.add(m);
        }
        return result;
    }

    /**
     * 建立绑定（多对多）：入参任意顺序传入酒店与打印店 id，按类型自动定向；重复绑定拒绝。
     */
    public void bind(Long aId, Long bId, AuthUser user) {
        if (aId == null || bId == null || aId.equals(bId)) {
            throw new BusinessException("绑定对象不合法");
        }
        Supplier a = this.getById(aId);
        Supplier b = this.getById(bId);
        if (a == null || b == null) {
            throw new BusinessException("供应商不存在");
        }
        String bizA = bizKeyOf(a.getTypeId());
        String bizB = bizKeyOf(b.getTypeId());
        Long hotelId;
        Long printId;
        if (BIZ_HOTEL.equals(bizA) && BIZ_PRINT.equals(bizB)) {
            hotelId = aId;
            printId = bId;
        } else if (BIZ_PRINT.equals(bizA) && BIZ_HOTEL.equals(bizB)) {
            hotelId = bId;
            printId = aId;
        } else {
            throw new BusinessException("绑定仅支持酒店与打印店之间建立（当前组合类型不匹配）");
        }
        Long dup = relMapper.selectCount(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                .eq(SupplierHotelPrintRel::getHotelId, hotelId)
                .eq(SupplierHotelPrintRel::getPrintShopId, printId));
        if (dup != null && dup > 0) {
            throw new BusinessException("两家供应商已绑定，不能重复绑定");
        }
        SupplierHotelPrintRel rel = new SupplierHotelPrintRel();
        rel.setHotelId(hotelId);
        rel.setPrintShopId(printId);
        rel.setCreateBy(user == null ? null : user.getUsername());
        rel.setCreateTime(LocalDateTime.now());
        relMapper.insert(rel);
    }

    /**
     * 解绑（双向可操作，与跳转互不影响）。
     */
    public void unbind(Long aId, Long bId) {
        if (aId == null || bId == null) {
            throw new BusinessException("解绑对象不合法");
        }
        relMapper.delete(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                .and(w -> w.eq(SupplierHotelPrintRel::getHotelId, aId).eq(SupplierHotelPrintRel::getPrintShopId, bId)
                        .or(o -> o.eq(SupplierHotelPrintRel::getHotelId, bId).eq(SupplierHotelPrintRel::getPrintShopId, aId))));
    }

    // ==================== 私有方法 ====================

    private String bizKeyOf(Long typeId) {
        if (typeId == null) {
            return null;
        }
        SupplierType t = typeMapper.selectById(typeId);
        return t == null ? null : t.getBizKey();
    }

    /**
     * 批量填充展示字段：类型名/业务键、城市名/省份、图片列表、平均评分与评价数（单次分组查询）。
     */
    private void enrich(List<Supplier> suppliers) {
        if (suppliers == null || suppliers.isEmpty()) {
            return;
        }
        List<Long> typeIds = suppliers.stream().map(Supplier::getTypeId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> cityIds = suppliers.stream().map(Supplier::getCityId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, SupplierType> typeMap = typeIds.isEmpty() ? Map.of()
                : typeMapper.selectBatchIds(typeIds).stream().collect(Collectors.toMap(SupplierType::getId, t -> t));
        Map<Long, SupplierCity> cityMap = cityIds.isEmpty() ? Map.of()
                : cityMapper.selectBatchIds(cityIds).stream().collect(Collectors.toMap(SupplierCity::getId, c -> c));

        List<Long> ids = suppliers.stream().map(Supplier::getId).collect(Collectors.toList());
        Map<Long, double[]> stats = reviewStats(ids);

        for (Supplier s : suppliers) {
            SupplierType t = typeMap.get(s.getTypeId());
            if (t != null) {
                s.setTypeName(t.getName());
                s.setTypeBizKey(t.getBizKey());
            }
            SupplierCity c = cityMap.get(s.getCityId());
            if (c != null) {
                s.setCityName(c.getName());
                s.setProvince(c.getProvince());
            }
            s.setImageList(parseImages(s.getImages()));
            double[] st = stats.get(s.getId());
            if (st != null && st[1] > 0) {
                s.setAvgStars(Math.round(st[0] / st[1] * 10) / 10.0);
                s.setReviewCount((long) st[1]);
            } else {
                s.setReviewCount(0L);
            }
        }
    }

    /**
     * 评价统计（supplierId -> [星级和, 条数]），单次全量拉取后内存分组（数据量小）。
     */
    private Map<Long, double[]> reviewStats(List<Long> supplierIds) {
        Map<Long, double[]> result = new HashMap<>();
        List<SupplierReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<SupplierReview>().in(SupplierReview::getSupplierId, supplierIds));
        for (SupplierReview r : reviews) {
            double[] arr = result.computeIfAbsent(r.getSupplierId(), k -> new double[2]);
            arr[0] += r.getStars() == null ? 0 : r.getStars();
            arr[1] += 1;
        }
        return result;
    }

    private List<String> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("数据序列化失败: " + e.getMessage());
        }
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
