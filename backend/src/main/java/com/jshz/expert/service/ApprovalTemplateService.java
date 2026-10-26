package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ApprovalTemplate;
import com.jshz.expert.mapper.ApprovalTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 审批申请字段模板服务（需求 R-B4）。
 * 模板字段必须来自专家可编辑白名单（ApplicationService.WHITELIST_FIELDS + PHOTO_FIELDS）；
 * 停用模板不可再用于新申请（提交时校验 enabled）。
 */
@Service
@RequiredArgsConstructor
public class ApprovalTemplateService extends ServiceImpl<ApprovalTemplateMapper, ApprovalTemplate> {

    /** 模板名称长度上限 */
    private static final int NAME_MAX_LEN = 100;

    private final ObjectMapper objectMapper;

    // ==================== 查询 ====================

    /**
     * 启用模板列表（员工提交申请下拉用），按 id 升序。
     */
    public List<ApprovalTemplate> listEnabled() {
        List<ApprovalTemplate> list = this.list(new LambdaQueryWrapper<ApprovalTemplate>()
                .eq(ApprovalTemplate::getEnabled, 1)
                .orderByAsc(ApprovalTemplate::getId));
        list.forEach(this::fillFields);
        return list;
    }

    /**
     * 全部模板列表（管理页用，含停用），按 id 升序。
     */
    public List<ApprovalTemplate> listAll() {
        List<ApprovalTemplate> list = this.list(
                new LambdaQueryWrapper<ApprovalTemplate>().orderByAsc(ApprovalTemplate::getId));
        list.forEach(this::fillFields);
        return list;
    }

    /**
     * 按 id 取单个模板并填充解析字段；不存在返回 null。
     */
    public ApprovalTemplate getFilled(Long id) {
        if (id == null) {
            return null;
        }
        ApprovalTemplate template = getById(id);
        if (template != null) {
            fillFields(template);
        }
        return template;
    }

    // ==================== 增删改 ====================

    /**
     * 新增模板：名称非空且唯一、字段非空且全部在白名单内、去重。
     */
    public ApprovalTemplate create(String templateName, List<String> fields, Integer enabled) {
        String name = validateName(templateName);
        List<String> normalized = validateFields(fields);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTemplateName(name);
        template.setFieldsJson(writeJson(normalized));
        template.setEnabled(normalizeEnabled(enabled));
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        save(template);
        return getFilled(template.getId());
    }

    /**
     * 更新模板（全量：名称/字段/启停），校验规则同新增。
     */
    public ApprovalTemplate update(Long id, String templateName, List<String> fields, Integer enabled) {
        ApprovalTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        String name = validateName(templateName);
        List<String> normalized = validateFields(fields);
        // 名称唯一校验（排除自身）
        ApprovalTemplate same = getOne(new LambdaQueryWrapper<ApprovalTemplate>()
                .eq(ApprovalTemplate::getTemplateName, name).last("LIMIT 1"));
        if (same != null && !same.getId().equals(id)) {
            throw new BusinessException("模板名称「" + name + "」已存在");
        }
        template.setTemplateName(name);
        template.setFieldsJson(writeJson(normalized));
        template.setEnabled(normalizeEnabled(enabled));
        template.setUpdateTime(LocalDateTime.now());
        updateById(template);
        return getFilled(id);
    }

    /**
     * 删除模板：仅删除模板本身；历史申请单上的 template_id 保留为来源记录，不受影响。
     */
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("模板不存在");
        }
        removeById(id);
    }

    // ==================== 校验与工具 ====================

    /**
     * 校验模板字段集合 ⊆ 指定模板字段（提交申请时用，需求③）。
     * 模板必须存在且启用；不在模板范围内的字段拒绝提交。
     */
    public ApprovalTemplate checkTemplateForSubmit(Long templateId, Set<String> changeKeys) {
        ApprovalTemplate template = getById(templateId);
        if (template == null || template.getEnabled() == null || template.getEnabled() != 1) {
            throw new BusinessException("所选申请模板不存在或已停用，请刷新模板后重试");
        }
        Set<String> allowed = new HashSet<>(parseFieldsJson(template.getFieldsJson()));
        for (String key : changeKeys) {
            if (!allowed.contains(key)) {
                throw new BusinessException("字段「" + key + "」不在模板「" + template.getTemplateName()
                        + "」范围内，请改用全部字段提交或更换模板");
            }
        }
        return template;
    }

    /**
     * 名称校验：非空、去首尾空格、长度 ≤ 100、不重名。
     */
    private String validateName(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            throw new BusinessException("模板名称不能为空");
        }
        String name = templateName.trim();
        if (name.length() > NAME_MAX_LEN) {
            throw new BusinessException("模板名称不能超过 " + NAME_MAX_LEN + " 字");
        }
        if (count(new LambdaQueryWrapper<ApprovalTemplate>().eq(ApprovalTemplate::getTemplateName, name)) > 0) {
            throw new BusinessException("模板名称「" + name + "」已存在");
        }
        return name;
    }

    /**
     * 字段校验：非空数组、去重、全部 ∈ 白名单（文本 + 照片）。
     */
    private List<String> validateFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("模板字段不能为空，请至少勾选一个字段");
        }
        Set<String> allowed = new HashSet<>(ApplicationService.WHITELIST_FIELDS);
        allowed.addAll(ApplicationService.PHOTO_FIELDS);
        Set<String> normalized = new LinkedHashSet<>();
        for (String field : fields) {
            if (!StringUtils.hasText(field)) {
                continue;
            }
            String key = field.trim();
            if (!allowed.contains(key)) {
                throw new BusinessException("字段「" + key + "」不在专家可编辑字段范围内");
            }
            normalized.add(key);
        }
        if (normalized.isEmpty()) {
            throw new BusinessException("模板字段不能为空，请至少勾选一个字段");
        }
        return new ArrayList<>(normalized);
    }

    private Integer normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }

    /**
     * 解析模板字段 JSON；非法 JSON 返回空数组。
     */
    public List<String> parseFieldsJson(String fieldsJson) {
        if (!StringUtils.hasText(fieldsJson)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(fieldsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 填充展示字段：fields（解析 JSON）。
     */
    public void fillFields(ApprovalTemplate template) {
        template.setFields(parseFieldsJson(template.getFieldsJson()));
    }

    private String writeJson(List<String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new BusinessException("模板字段序列化失败: " + e.getMessage());
        }
    }
}
