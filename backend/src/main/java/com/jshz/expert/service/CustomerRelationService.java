package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Map;
import java.util.Set;

/**
 * 客户关系服务：关系类型管理（预置枚举 + 维护人可扩展，可停用不可物理删除）+ 有向关系维护 + 关系图数据。
 * 关系为独立结构化数据（关系类型、关系说明、方向：from -> to）。
 */
@Service
@RequiredArgsConstructor
public class CustomerRelationService {

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
     * 新增自定义关系类型（编码由后端生成 custom_<时间戳>，默认启用）。
     */
    public CustomerRelationType createType(CustomerRelationTypeForm form, AuthUser authUser) {
        if (!StringUtils.hasText(form.getName())) {
            throw new BusinessException("关系类型名称不能为空");
        }
        String name = form.getName().trim();
        if (name.length() > 50) {
            throw new BusinessException("关系类型名称过长（≤50字）");
        }
        CustomerRelationType type = new CustomerRelationType();
        type.setName(name);
        type.setCode("custom_" + Instant.now().toEpochMilli());
        type.setBuiltin(0);
        type.setStatus(1);
        type.setCreateBy(authUser == null ? null : authUser.getUsername());
        relationTypeMapper.insert(type);
        return type;
    }

    /**
     * 编辑关系类型：可改名称与状态（1启用 0停用）；停用后不可用于新建关系，已有关系保留。
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
        relationTypeMapper.updateById(type);
        return type;
    }

    // ==================== 关系维护 ====================

    /**
     * 建立有向关系（from -> to）。
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
        // 幂等：同一 from-to-类型 已存在时直接返回已有关系
        CustomerRelation existing = relationMapper.selectOne(new LambdaQueryWrapper<CustomerRelation>()
                .eq(CustomerRelation::getFromCustomerId, form.getFromCustomerId())
                .eq(CustomerRelation::getToCustomerId, form.getToCustomerId())
                .eq(CustomerRelation::getRelationTypeId, form.getRelationTypeId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        CustomerRelation relation = new CustomerRelation();
        relation.setFromCustomerId(form.getFromCustomerId());
        relation.setToCustomerId(form.getToCustomerId());
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

    /**
     * 以某客户为中心的关系圈脑图：
     * center 中心节点；nodes 中心 + 直接邻居（去重）；links 中心相关的全部有向关系。
     */
    public Map<String, Object> centerGraph(Long customerId) {
        Customer center = visibleCustomer(customerId);
        List<CustomerRelation> all = relationMapper.selectList(new LambdaQueryWrapper<CustomerRelation>()
                .and(w -> w.eq(CustomerRelation::getFromCustomerId, customerId)
                        .or().eq(CustomerRelation::getToCustomerId, customerId)));
        Map<Long, Customer> nodeMap = new LinkedHashMap<>();
        nodeMap.put(center.getId(), center);
        List<Map<String, Object>> links = new ArrayList<>();
        for (CustomerRelation r : all) {
            if (visibleSafe(r.getFromCustomerId()) != null) {
                nodeMap.putIfAbsent(r.getFromCustomerId(), visibleSafe(r.getFromCustomerId()));
            }
            if (visibleSafe(r.getToCustomerId()) != null) {
                nodeMap.putIfAbsent(r.getToCustomerId(), visibleSafe(r.getToCustomerId()));
            }
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("id", r.getId());
            link.put("source", r.getFromCustomerId());
            link.put("target", r.getToCustomerId());
            link.put("relationTypeId", r.getRelationTypeId());
            link.put("relationTypeName", typeName(r.getRelationTypeId()));
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
     * 超过 500 客户时截断并给出提示，避免前端渲染卡顿。
     */
    public Map<String, Object> networkGraph() {
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getDeleted, 0)
                .orderByAsc(Customer::getId)
                .last("LIMIT " + NETWORK_NODE_LIMIT));
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
            link.put("description", r.getDescription());
            link.put("direction", "from_" + r.getFromCustomerId() + "_to_" + r.getToCustomerId());
            links.add(link);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", nodes);
        data.put("links", links);
        data.put("truncated", customers.size() >= NETWORK_NODE_LIMIT);
        return data;
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
}
