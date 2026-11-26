package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.ProjectSupplier;
import com.jshz.expert.mapper.ProjectSupplierMapper;
import com.jshz.expert.entity.SupplierCity;
import com.jshz.expert.entity.SupplierHotelPrintRel;
import com.jshz.expert.entity.SupplierImage;
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
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商管理服务：档案（全员维护）、类型/城市字典（只读）、行价、评价、酒店-打印店多对多绑定。
 * 权限：档案/行价/评价/绑定 登录即可；类型管理由接口层 supplier:dict 权限码控制；城市字典固定只读（禁止增删）。
 */
@Service
@RequiredArgsConstructor
public class SupplierService extends ServiceImpl<SupplierMapper, Supplier> {

    /** 酒店类型业务键 */
    public static final String BIZ_HOTEL = "HOTEL";

    /** 打印店类型业务键 */
    public static final String BIZ_PRINT = "PRINT";

    private final ProjectSupplierMapper projectSupplierMapper;
    private final SupplierTypeMapper typeMapper;
    private final SupplierCityMapper cityMapper;
    private final SupplierPriceItemMapper priceItemMapper;
    private final SupplierReviewMapper reviewMapper;
    private final SupplierHotelPrintRelMapper relMapper;
    private final SupplierStorageService storageService;
    private final SupplierImageService imageService;

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

    // ==================== 城市字典（固定只读） ====================

    /**
     * 城市列表（按 id 升序保持预置省份分组顺序；前端按 province 分组展示）。
     * province 非空时按省份过滤，支持省→市两级联动。
     */
    public List<SupplierCity> listCities(String province) {
        LambdaQueryWrapper<SupplierCity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(province)) {
            wrapper.eq(SupplierCity::getProvince, province.trim());
        }
        return cityMapper.selectList(wrapper.orderByAsc(SupplierCity::getId));
    }

    /**
     * 按城市名解析城市 id：仅精确匹配字典（同名唯一）；未收录时明确报错，禁止自动沉淀。
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
        throw new BusinessException("城市「" + name + "」未收录，城市字典固定只读，请联系管理员在种子数据中补充");
    }

    // ==================== 供应商档案 ====================

    /**
     * 分页列表：keyword（名称/地址/联系人模糊）+ 类型 + 城市 + 仅看已评价 组合筛选，附带类型/城市名与评分统计。
     * sort：name（默认，按名称升序）/ updated（按更新时间倒序）。
     */
    public Page<Supplier> pageSuppliers(String keyword, Long typeId, Long cityId,
                                        boolean onlyReviewed, String sort, long page, long size) {
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
        if (onlyReviewed) {
            // 仅看已评价：存在 supplier_review 记录（子查询，避免与分页 count 冲突）
            wrapper.inSql(Supplier::getId, "SELECT supplier_id FROM supplier_review");
        }
        if ("updated".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(Supplier::getUpdateTime).orderByDesc(Supplier::getId);
        } else {
            wrapper.orderByAsc(Supplier::getName).orderByAsc(Supplier::getId);
        }
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
        // 部分更新语义：仅校验/应用传入字段（图片由 supplier_image 表独立管理，不再走 images JSON）
        applyBody(s, body, false);
        this.updateById(s);
        return this.getById(id);
    }

    /**
     * 从请求体填充字段：name/typeId/cityId|cityName(仅字典精确匹配)/address/contact/phone/wechat/description/images。
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
            Long typeId = ParamUtils.toLong(body.get("typeId"));
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
                Long cityId = ParamUtils.toLong(body.get("cityId"));
                if (cityId == null || cityMapper.selectById(cityId) == null) {
                    throw new BusinessException("城市不存在");
                }
                s.setCityId(cityId);
            } else {
                // 城市名：仅精确匹配字典；未收录明确报错（不再自动沉淀）
                s.setCityId(resolveCityIdByName(String.valueOf(body.get("cityName"))));
            }
        } else if (creating) {
            throw new BusinessException("城市不能为空");
        }
        if (body.containsKey("address")) {
            s.setAddress(ParamUtils.str(body.get("address")));
        }
        if (body.containsKey("contact")) {
            s.setContact(ParamUtils.str(body.get("contact")));
        }
        if (body.containsKey("phone")) {
            s.setPhone(ParamUtils.str(body.get("phone")));
        }
        if (body.containsKey("wechat")) {
            s.setWechat(ParamUtils.str(body.get("wechat")));
        }
        if (body.containsKey("description")) {
            s.setDescription(ParamUtils.str(body.get("description")));
        }
        // v2.0「三、图片与主图」：图片不再通过 supplier.images JSON 数组管理，
        // 统一走 supplier_image 表（上传/设主图/删除接口）。兼容历史请求体，忽略 images 字段。
    }

    /**
     * 删除供应商：级联删除行价/评价/绑定关系与图片文件。
     */
    /** 行锁加载供应商（FOR UPDATE）：删除/行价全量替换等"读-改-写"路径统一入口，防并发双写。 */
    private Supplier lockSupplier(Long id) {
        Supplier s = baseMapper.selectOne(new LambdaQueryWrapper<Supplier>().eq(Supplier::getId, id).last("FOR UPDATE"));
        if (s == null) {
            throw new BusinessException("供应商不存在");
        }
        return s;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSupplier(Long id) {
        Supplier s = lockSupplier(id);
        if (projectSupplierMapper.selectCount(new LambdaQueryWrapper<ProjectSupplier>()
                .eq(ProjectSupplier::getSupplierId, id)) > 0) {
            throw new BusinessException("供应商已被项目引用，请先处理项目关联，不能删除");
        }
        priceItemMapper.delete(new LambdaQueryWrapper<SupplierPriceItem>().eq(SupplierPriceItem::getSupplierId, id));
        reviewMapper.delete(new LambdaQueryWrapper<SupplierReview>().eq(SupplierReview::getSupplierId, id));
        relMapper.delete(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                .eq(SupplierHotelPrintRel::getHotelId, id).or().eq(SupplierHotelPrintRel::getPrintShopId, id));
        this.removeById(id);
        // v2.0「三、图片与主图」：图片登记于 supplier_image 表，级联删除 DB 行并同步删除物理文件
        imageService.deleteAll(id);
    }

    // ==================== 行价 ====================

    public List<SupplierPriceItem> priceItemsOf(Long supplierId) {
        return priceItemMapper.selectList(new LambdaQueryWrapper<SupplierPriceItem>()
                .eq(SupplierPriceItem::getSupplierId, supplierId)
                .orderByAsc(SupplierPriceItem::getSort).orderByAsc(SupplierPriceItem::getId));
    }

    /**
     * 保存行价（全量替换）：[{itemName, price, unit, remark, quoteDate, validUntil, sort}]。
     * quoteDate/validUntil 为 yyyy-MM-dd，可空（NULL=未填写 / 长期有效）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SupplierPriceItem> savePrices(Long supplierId, List<Map<String, Object>> items) {
        // P2-2（2026-11-26 审查修复）：先删后插前锁供应商行，并发保存串行化，避免双份行价
        Supplier s = lockSupplier(supplierId);
        priceItemMapper.delete(new LambdaQueryWrapper<SupplierPriceItem>().eq(SupplierPriceItem::getSupplierId, supplierId));
        int sort = 0;
        if (items != null) {
            for (Map<String, Object> item : items) {
                String itemName = ParamUtils.str(item.get("itemName"));
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
                p.setUnit(ParamUtils.str(item.get("unit")));
                p.setRemark(ParamUtils.str(item.get("remark")));
                LocalDate quoteDate = parseDate(item.get("quoteDate"), itemName, "报价日期");
                LocalDate validUntil = parseDate(item.get("validUntil"), itemName, "报价有效期");
                if (quoteDate != null && validUntil != null && validUntil.isBefore(quoteDate)) {
                    throw new BusinessException("行价「" + itemName + "」的有效期不能早于报价日期");
                }
                p.setQuoteDate(quoteDate);
                p.setValidUntil(validUntil);
                Object st = item.get("sort");
                p.setSort(st instanceof Number n ? n.intValue() : sort);
                priceItemMapper.insert(p);
                sort++;
            }
        }
        return priceItemsOf(supplierId);
    }

    /** 行价日期解析：接受 yyyy-MM-dd 或带时间的字符串，空值返回 null。 */
    private LocalDate parseDate(Object raw, String itemName, String fieldName) {
        String text = ParamUtils.str(raw);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String day = text.trim().length() > 10 ? text.trim().substring(0, 10) : text.trim();
        try {
            return LocalDate.parse(day);
        } catch (DateTimeParseException e) {
            throw new BusinessException("行价「" + itemName + "」的" + fieldName + "格式应为 yyyy-MM-dd");
        }
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
        // P2-1（2026-11-26 审查修复）：与 supplier_review.content varchar(500) 同口径，超长给出业务错误而非 500
        if (content.trim().length() > 500) {
            throw new BusinessException("评价内容不能超过 500 字");
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
            m.put("peerCityId", t.getCityId());
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
        // v2.1「三、酒店-打印店同城绑定」：两端城市必须一致，拒绝跨城绑定（存量跨城关系不受影响）；
        // P3-2：复用上方已加载的 a/b，不再重复查库
        Supplier hotel = hotelId.equals(aId) ? a : b;
        Supplier printShop = printId.equals(aId) ? a : b;
        if (!java.util.Objects.equals(hotel.getCityId(), printShop.getCityId())) {
            throw new BusinessException(400, "BIND_CITY_MISMATCH：仅支持绑定同城市的打印店/酒店");
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

    /**
     * 跨城绑定清单（v2.1）：全部存量绑定中 hotel.city_id != print_shop.city_id 的关系。
     * 支持按酒店/打印店名称关键字模糊过滤；返回分页 {total, items}（数据量小，全量拉取后内存过滤分页）。
     * 每项含 relId/hotelId/hotelName/hotelCity/printShopId/printShopName/printShopCity/createBy/createTime。
     */
    public Page<Map<String, Object>> pageCrossCityBindings(String keyword, long page, long size) {
        List<SupplierHotelPrintRel> rels = relMapper.selectList(new LambdaQueryWrapper<SupplierHotelPrintRel>()
                .orderByDesc(SupplierHotelPrintRel::getCreateTime).orderByDesc(SupplierHotelPrintRel::getId));
        List<Map<String, Object>> all = new ArrayList<>();
        if (!rels.isEmpty()) {
            List<Long> supplierIds = new ArrayList<>();
            for (SupplierHotelPrintRel r : rels) {
                supplierIds.add(r.getHotelId());
                supplierIds.add(r.getPrintShopId());
            }
            Map<Long, Supplier> supplierMap = this.listByIds(supplierIds).stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s));
            List<Long> cityIds = supplierMap.values().stream()
                    .map(Supplier::getCityId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
            Map<Long, SupplierCity> cityMap = cityIds.isEmpty() ? Map.of()
                    : cityMapper.selectBatchIds(cityIds).stream().collect(Collectors.toMap(SupplierCity::getId, c -> c));
            String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
            for (SupplierHotelPrintRel r : rels) {
                Supplier hotel = supplierMap.get(r.getHotelId());
                Supplier print = supplierMap.get(r.getPrintShopId());
                if (hotel == null || print == null) {
                    continue;
                }
                if (java.util.Objects.equals(hotel.getCityId(), print.getCityId())) {
                    continue;
                }
                if (kw != null
                        && !(hotel.getName() != null && hotel.getName().contains(kw))
                        && !(print.getName() != null && print.getName().contains(kw))) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("relId", r.getId());
                m.put("hotelId", hotel.getId());
                m.put("hotelName", hotel.getName());
                m.put("hotelCity", cityNameOf(cityMap, hotel.getCityId()));
                m.put("printShopId", print.getId());
                m.put("printShopName", print.getName());
                m.put("printShopCity", cityNameOf(cityMap, print.getCityId()));
                m.put("createBy", r.getCreateBy());
                m.put("createTime", r.getCreateTime());
                all.add(m);
            }
        }
        long total = all.size();
        int from = (int) Math.min((page - 1) * size, total);
        int to = (int) Math.min(page * size, total);
        Page<Map<String, Object>> result = new Page<>(page, size);
        result.setTotal(total);
        result.setRecords(from >= to ? List.of() : all.subList(from, to));
        return result;
    }

    /**
     * 绑定概览（需求四 2026-11-26）：酒店↔打印店双类型中"未绑定任何对端"的供应商清单。
     * cityId 可选过滤；返回项含 id/name/类型业务键/城市，前端按城市分组展示反向核对缺口。
     * 数据量小（现 125 家），全量内存计算；仅 HOTEL/PRINT 两类参与（绑定语义范围）。
     */
    public List<Map<String, Object>> unboundBindingPeers(Long cityId) {
        List<SupplierType> types = typeMapper.selectList(null);
        Map<Long, String> bizByType = types.stream()
                .filter(t -> StringUtils.hasText(t.getBizKey()))
                .collect(Collectors.toMap(SupplierType::getId, SupplierType::getBizKey, (x, y) -> x));
        Set<Long> bpTypeIds = bizByType.entrySet().stream()
                .filter(e -> BIZ_HOTEL.equals(e.getValue()) || BIZ_PRINT.equals(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (bpTypeIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Supplier> w = new LambdaQueryWrapper<Supplier>().in(Supplier::getTypeId, bpTypeIds);
        if (cityId != null) {
            w.eq(Supplier::getCityId, cityId);
        }
        List<Supplier> candidates = this.list(w);
        if (candidates.isEmpty()) {
            return List.of();
        }
        // 一次拉全绑定关系，统计每家出现次数（hotel 或 print 任一侧出现即算已绑定）
        Map<Long, Long> boundCount = new HashMap<>();
        for (SupplierHotelPrintRel r : relMapper.selectList(null)) {
            if (r.getHotelId() != null) boundCount.merge(r.getHotelId(), 1L, Long::sum);
            if (r.getPrintShopId() != null) boundCount.merge(r.getPrintShopId(), 1L, Long::sum);
        }
        // 城市名映射
        List<Long> cityIds = candidates.stream().map(Supplier::getCityId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, SupplierCity> cityMap = cityIds.isEmpty() ? Map.of()
                : cityMapper.selectBatchIds(cityIds).stream()
                        .collect(Collectors.toMap(SupplierCity::getId, c -> c));
        Map<Long, String> nameByType = types.stream()
                .collect(Collectors.toMap(SupplierType::getId, SupplierType::getName, (x, y) -> x));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Supplier s : candidates) {
            if (boundCount.getOrDefault(s.getId(), 0L) > 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("typeBizKey", bizByType.get(s.getTypeId()));
            m.put("typeName", nameByType.get(s.getTypeId()));
            m.put("cityId", s.getCityId());
            SupplierCity c = s.getCityId() == null ? null : cityMap.get(s.getCityId());
            m.put("cityName", c == null ? null : c.getName());
            result.add(m);
        }
        // 城市 → 类型 → 名称排序，便于前端直接分组
        result.sort(java.util.Comparator
                .comparing((Map<String, Object> m) -> String.valueOf(m.get("cityName")))
                .thenComparing(m -> String.valueOf(m.get("typeBizKey")))
                .thenComparing(m -> String.valueOf(m.get("name"))));
        return result;
    }

    /**
     * 批量解绑（v2.1）：body {relIds}，事务内统一校验后逐条删除。
     * relIds 为空返回 PARAM_INVALID；含不存在的关系整体失败回滚并返回 BINDING_NOT_FOUND。
     */
    @Transactional(rollbackFor = Exception.class)
    public int unbindBatch(List<Long> relIds) {
        if (relIds == null || relIds.isEmpty()) {
            throw new BusinessException(400, "PARAM_INVALID：参数不合法");
        }
        List<Long> distinctIds = relIds.stream().distinct().collect(Collectors.toList());
        List<SupplierHotelPrintRel> rels = relMapper.selectBatchIds(distinctIds);
        if (rels.size() != distinctIds.size()) {
            throw new BusinessException(404, "BINDING_NOT_FOUND：绑定关系不存在");
        }
        relMapper.deleteBatchIds(distinctIds);
        return distinctIds.size();
    }

    private String cityNameOf(Map<Long, SupplierCity> cityMap, Long cityId) {
        SupplierCity c = cityId == null ? null : cityMap.get(cityId);
        return c == null ? null : c.getName();
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
        Map<Long, List<SupplierImage>> imageMap = imageService.imageMap(ids);

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
            List<SupplierImage> imgs = imageMap.get(s.getId());
            if (imgs == null || imgs.isEmpty()) {
                s.setImageList(List.of());
                s.setMainImage(null);
            } else {
                s.setImageList(imgs.stream().map(SupplierImage::getFileName).collect(Collectors.toList()));
                s.setMainImage(imgs.stream()
                        .filter(i -> i.getIsMain() != null && i.getIsMain() == 1)
                        .map(SupplierImage::getFileName).findFirst().orElse(imgs.get(0).getFileName()));
            }
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
}
