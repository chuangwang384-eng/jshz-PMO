package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.CustomerRelationForm;
import com.jshz.expert.dto.CustomerRelationTypeForm;
import com.jshz.expert.entity.Customer;
import com.jshz.expert.entity.CustomerRelation;
import com.jshz.expert.entity.CustomerRelationType;
import com.jshz.expert.mapper.CustomerMapper;
import com.jshz.expert.mapper.CustomerRelationMapper;
import com.jshz.expert.mapper.CustomerRelationTypeMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 客户关系服务：关系类型管理（预置枚举 + 维护人可扩展，可停用不可物理删除）+ 有向关系维护 + 关系图数据。
 * 关系为独立结构化数据（关系类型、关系说明、方向：from -> to）。
 */
@Service
@RequiredArgsConstructor
public class CustomerRelationService {

    /** 关系方向：单向有向（from -> to，如介绍关系/上下级） */
    public static final String DIRECTION_DIRECTED = "DIRECTED";

    /** 关系方向：双向（双方互为关系，如业务伙伴） */
    public static final String DIRECTION_BIDIRECTED = "BIDIRECTED";

    /** 关系方向：无向（不分方向，如同事/亲友） */
    public static final String DIRECTION_UNDIRECTED = "UNDIRECTED";

    private static final int NETWORK_NODE_LIMIT = 500;

    private final CustomerRelationMapper relationMapper;
    private final CustomerRelationTypeMapper relationTypeMapper;
    private final CustomerMapper customerMapper;

    // ==================== 关系类型管理 ====================

    /**
     * 关系类型列表（含停用类型，前端区分展示；预置类型 builtin=1 不可物理删除）。
     */
    public List<CustomerRelationType> listTypes() {
        return relationTypeMapper.selectList(new LambdaQueryWrapper<CustomerRelationType>()
                .orderByDesc(CustomerRelationType::getStatus)
                .orderByDesc(CustomerRelationType::getBuiltin)
                .orderByAsc(CustomerRelationType::getId));
    }

    /**
     * 新增自定义关系类型（编码由后端生成 custom_<时间戳>，默认启用；方向缺省 DIRECTED）。
     */
    public CustomerRelationType createType(CustomerRelationTypeForm form, AuthUser authUser) {
        if (!StringUtils.hasText(form.getName())) {
            throw new BusinessException("关系类型名称不能为空");
        }
        String name = form.getName().trim();
        if (name.length() > 50) {
            throw new BusinessException("关系类型名称过长（≤50字）");
        }
        String direction = normalizeDirection(form.getDirection());
        if (direction == null) {
            direction = DIRECTION_DIRECTED;
        }
        CustomerRelationType type = new CustomerRelationType();
        type.setName(name);
        type.setCode("custom_" + Instant.now().toEpochMilli());
        type.setBuiltin(0);
        type.setStatus(1);
        type.setDirection(direction);
        type.setCreateBy(authUser == null ? null : authUser.getUsername());
        relationTypeMapper.insert(type);
        return type;
    }

    /**
     * 编辑关系类型：可改名称、状态（1启用 0停用）与方向；预置类型（builtin=1）方向禁改。
     * 停用后不可用于新建关系，已有关系保留。
     */
    public CustomerRelationType updateType(Long id, CustomerRelationTypeForm form, AuthUser authUser) {
        CustomerRelationType type = relationTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException("关系类型不存在");
        }
        if (form.getStatus() != null) {
            if (form.getStatus() != 0 && form.getStatus() != 1) {
                throw new BusinessException("非法状态值");
            }
            type.setStatus(form.getStatus());
        }
        if (StringUtils.hasText(form.getName())) {
            String name = form.getName().trim();
            if (name.length() > 50) {
                throw new BusinessException("关系类型名称过长（≤50字）");
            }
            type.setName(name);
        }
        if (StringUtils.hasText(form.getDirection())) {
            if (type.getBuiltin() != null && type.getBuiltin() == 1) {
                throw new BusinessException("预置类型的方向不可修改");
            }
            type.setDirection(normalizeDirection(form.getDirection()));
        }
        relationTypeMapper.updateById(type);
        return type;
    }

    // ==================== 关系维护 ====================

    /**
     * 建立客户关系。方向语义由关系类型 direction 决定：
     * DIRECTED 按 from:to:type 判重（有向）；BIDIRECTED/UNDIRECTED 按 min:max:type 判重（双向/无向建单条双方互见）。
     * 已存在时抛"该关系已存在"提示，前端据此处理幂等。
     */
    public CustomerRelation createRelation(CustomerRelationForm form, AuthUser authUser) {
        if (form.getFromCustomerId() == null || form.getToCustomerId() == null) {
            throw new BusinessException("关系两端客户不能为空");
        }
        if (form.getFromCustomerId().equals(form.getToCustomerId())) {
            throw new BusinessException("不能与自己建立关系");
        }
        if (form.getRelationTypeId() == null) {
            throw new BusinessException("请选择关系类型");
        }
        Customer from = visibleCustomer(form.getFromCustomerId());
        Customer to = visibleCustomer(form.getToCustomerId());
        CustomerRelationType type = relationTypeMapper.selectById(form.getRelationTypeId());
        if (type == null || type.getStatus() == null || type.getStatus() != 1) {
            throw new BusinessException("关系类型不存在或已停用");
        }
        String direction = resolveDirection(type);
        Long fromId = form.getFromCustomerId();
        Long toId = form.getToCustomerId();
        // 幂等判重：单向按 from:to:type；双向/无向按 min:max:type（任意顺序视为同一关系）
        CustomerRelation existing;
        if (DIRECTION_DIRECTED.equals(direction)) {
            existing = relationMapper.selectOne(new LambdaQueryWrapper<CustomerRelation>()
                    .eq(CustomerRelation::getFromCustomerId, fromId)
                    .eq(CustomerRelation::getToCustomerId, toId)
                    .eq(CustomerRelation::getRelationTypeId, form.getRelationTypeId())
                    .last("LIMIT 1"));
        } else {
            Long min = Math.min(fromId, toId);
            Long max = Math.max(fromId, toId);
            existing = relationMapper.selectOne(new LambdaQueryWrapper<CustomerRelation>()
                    .eq(CustomerRelation::getRelationTypeId, form.getRelationTypeId())
                    .and(w -> w.eq(CustomerRelation::getFromCustomerId, min)
                                    .eq(CustomerRelation::getToCustomerId, max)
                            .or().eq(CustomerRelation::getFromCustomerId, max)
                                    .eq(CustomerRelation::getToCustomerId, min))
                    .last("LIMIT 1"));
        }
        if (existing != null) {
            throw new BusinessException("该关系已存在，无需重复建立");
        }
        CustomerRelation relation = new CustomerRelation();
        relation.setFromCustomerId(fromId);
        relation.setToCustomerId(toId);
        relation.setRelationTypeId(form.getRelationTypeId());
        relation.setDescription(trimToNull(form.getDescription()));
        relation.setCreateBy(authUser == null ? null : authUser.getUsername());
        relationMapper.insert(relation);
        return relation;
    }

    /**
     * 删除关系（关系记录可删除，用于连线误操作修正）。
     */
    public void deleteRelation(Long id, AuthUser authUser) {
        CustomerRelation relation = relationMapper.selectById(id);
        if (relation == null) {
            throw new BusinessException("关系不存在");
        }
        relationMapper.deleteById(id);
    }

    // ==================== 关系图数据 ====================

    /** 统计两端均有效的关系，分页列表与中心关系圈使用相同的可见范围。 */
    public Map<Long, Long> countByCustomerIds(java.util.Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> pageIds = new HashSet<>(customerIds);
        List<CustomerRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<CustomerRelation>()
                .and(w -> w.in(CustomerRelation::getFromCustomerId, pageIds)
                        .or().in(CustomerRelation::getToCustomerId, pageIds)));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> endpointIds = new HashSet<>();
        for (CustomerRelation relation : relations) {
            endpointIds.add(relation.getFromCustomerId());
            endpointIds.add(relation.getToCustomerId());
        }
        Set<Long> activeIds = new HashSet<>();
        for (Customer customer : customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .select(Customer::getId)
                .in(Customer::getId, endpointIds)
                .and(w -> w.ne(Customer::getDeleted, 1).or().isNull(Customer::getDeleted)))) {
            activeIds.add(customer.getId());
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (CustomerRelation relation : relations) {
            Long from = relation.getFromCustomerId();
            Long to = relation.getToCustomerId();
            if (!activeIds.contains(from) || !activeIds.contains(to)) {
                continue;
            }
            if (pageIds.contains(from)) {
                counts.merge(from, 1L, Long::sum);
            }
            if (!from.equals(to) && pageIds.contains(to)) {
                counts.merge(to, 1L, Long::sum);
            }
        }
        return counts;
    }

    /** 以指定客户为中心，返回客户及其一度关系。 */
    public Map<String, Object> centerGraph(Long customerId) {
        Customer center = visibleCustomer(customerId);
        List<CustomerRelation> all = relationMapper.selectList(new LambdaQueryWrapper<CustomerRelation>()
                .and(w -> w.eq(CustomerRelation::getFromCustomerId, customerId)
                        .or().eq(CustomerRelation::getToCustomerId, customerId)));
        Map<Long, Customer> nodeMap = new LinkedHashMap<>();
        nodeMap.put(center.getId(), center);
        List<Map<String, Object>> links = new ArrayList<>();
        for (CustomerRelation r : all) {
            Customer fromC = visibleSafe(r.getFromCustomerId());
            Customer toC = visibleSafe(r.getToCustomerId());
            // 任一端客户已软删/不存在时跳过该关系，避免渲染出指向不存在节点的隐形连线
            if (fromC == null || toC == null) {
                continue;
            }
            nodeMap.putIfAbsent(r.getFromCustomerId(), fromC);
            nodeMap.putIfAbsent(r.getToCustomerId(), toC);
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("id", r.getId());
            link.put("source", r.getFromCustomerId());
            link.put("target", r.getToCustomerId());
            link.put("relationTypeId", r.getRelationTypeId());
            link.put("relationTypeName", typeName(r.getRelationTypeId()));
            link.put("relationDirection", typeDirection(r.getRelationTypeId()));
            link.put("description", r.getDescription());
            link.put("direction", "from_" + r.getFromCustomerId() + "_to_" + r.getToCustomerId());
            links.add(link);
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Customer c : nodeMap.values()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", c.getId());
            n.put("name", c.getName());
            n.put("workUnit", c.getWorkUnit());
            n.put("isCenter", c.getId().equals(center.getId()));
            nodes.add(n);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("center", centerMap(center));
        data.put("nodes", nodes);
        data.put("links", links);
        return data;
    }

    /**
     * 全局关系网络脑图：全部未软删客户 + 两端均未软删的有向关系。
     * 超过节点上限时截断并给出提示，避免前端渲染卡顿。
     */
    public Map<String, Object> networkGraph() {
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getDeleted, 0)
                .orderByAsc(Customer::getId)
                .last("LIMIT " + (NETWORK_NODE_LIMIT + 1)));
        boolean truncated = customers.size() > NETWORK_NODE_LIMIT;
        if (truncated) {
            customers = customers.subList(0, NETWORK_NODE_LIMIT);
        }
        Set<Long> ids = new HashSet<>();
        for (Customer c : customers) {
            ids.add(c.getId());
        }
        List<CustomerRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<CustomerRelation>()
                .orderByAsc(CustomerRelation::getId));
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Customer c : customers) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", c.getId());
            n.put("name", c.getName());
            n.put("workUnit", c.getWorkUnit());
            nodes.add(n);
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (CustomerRelation r : relations) {
            if (!ids.contains(r.getFromCustomerId()) || !ids.contains(r.getToCustomerId())) {
                continue;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("id", r.getId());
            link.put("source", r.getFromCustomerId());
            link.put("target", r.getToCustomerId());
            link.put("relationTypeId", r.getRelationTypeId());
            link.put("relationTypeName", typeName(r.getRelationTypeId()));
            link.put("relationDirection", typeDirection(r.getRelationTypeId()));
            link.put("description", r.getDescription());
            link.put("direction", "from_" + r.getFromCustomerId() + "_to_" + r.getToCustomerId());
            links.add(link);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", nodes);
        data.put("links", links);
        data.put("truncated", truncated);
        return data;
    }

    // ==================== 目标客户搜索 ====================

    /**
     * 搜索可选目标客户：排除自身，支持关键字分页。已有关系按类型和方向在创建时判重。
     * customerId 为空时（新建档案关联场景，尚无自身 id）返回全部未软删客户。
     */
    public Page<Customer> searchTargets(Long customerId, String keyword, long page, long pageSize) {
        return searchTargets(customerId, keyword, false, page, pageSize);
    }

    public Page<Customer> searchTargets(Long customerId, String keyword, boolean identityOnly,
                                         long page, long pageSize) {
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        qw.eq(Customer::getDeleted, 0);
        if (customerId != null) {
            visibleCustomer(customerId);
            qw.ne(Customer::getId, customerId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> {
                w.like(Customer::getName, kw).or().like(Customer::getWorkUnit, kw);
                if (!identityOnly) {
                    w.or().like(Customer::getWorkPosition, kw)
                            .or().like(Customer::getWorkLevel, kw)
                            .or().like(Customer::getCurrentDuty, kw)
                            .or().like(Customer::getPreferences, kw)
                            .or().like(Customer::getRemark, kw);
                }
            });
        }
        qw.orderByDesc(Customer::getCreateTime, Customer::getId);
        return customerMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    // ==================== 私有辅助 ====================

    private Customer visibleCustomer(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null || (customer.getDeleted() != null && customer.getDeleted() == 1)) {
            throw new BusinessException("客户档案不存在或已删除");
        }
        return customer;
    }

    private Customer visibleSafe(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null || (customer.getDeleted() != null && customer.getDeleted() == 1)) {
            return null;
        }
        return customer;
    }

    private String typeName(Long typeId) {
        CustomerRelationType type = relationTypeMapper.selectById(typeId);
        return type == null ? "未知类型" : type.getName();
    }

    private String typeDirection(Long typeId) {
        CustomerRelationType type = relationTypeMapper.selectById(typeId);
        return type == null ? DIRECTION_DIRECTED : resolveDirection(type);
    }

    private Map<String, Object> centerMap(Customer center) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", center.getId());
        c.put("name", center.getName());
        c.put("workUnit", center.getWorkUnit());
        c.put("workPosition", center.getWorkPosition());
        c.put("workLevel", center.getWorkLevel());
        c.put("currentDuty", center.getCurrentDuty());
        return c;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析关系实际方向：非双向/无向一律视为单向有向（兼容旧数据 direction 为空）。
     */
    private String resolveDirection(CustomerRelationType type) {
        if (type.getDirection() != null
                && (DIRECTION_BIDIRECTED.equals(type.getDirection()) || DIRECTION_UNDIRECTED.equals(type.getDirection()))) {
            return type.getDirection();
        }
        return DIRECTION_DIRECTED;
    }

    /**
     * 规范化方向入参：空值返回 null；非法值抛异常。
     */
    private String normalizeDirection(String direction) {
        if (!StringUtils.hasText(direction)) {
            return null;
        }
        String d = direction.trim().toUpperCase(Locale.ROOT);
        if (DIRECTION_DIRECTED.equals(d) || DIRECTION_BIDIRECTED.equals(d) || DIRECTION_UNDIRECTED.equals(d)) {
            return d;
        }
        throw new BusinessException("非法关系方向（仅支持 DIRECTED/BIDIRECTED/UNDIRECTED）");
    }
}
