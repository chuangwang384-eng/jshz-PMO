package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.CustomerForm;
import com.jshz.expert.dto.CustomerRelationForm;
import com.jshz.expert.entity.Customer;
import com.jshz.expert.entity.CustomerAttachment;
import com.jshz.expert.mapper.CustomerAttachmentMapper;
import com.jshz.expert.mapper.CustomerMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户档案服务：新增/编辑/软删除/恢复/分页检索/附件管理。
 * 软删除默认隐藏（deleted=1），可恢复；普通员工不可删除（由权限码 customer:manage 控制）。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerMapper customerMapper;
    private final CustomerAttachmentMapper attachmentMapper;
    private final CustomerAttachmentStorageService storageService;
    private final CustomerRelationService relationService;

    /**
     * 分页检索客户档案。
     *
     * @param keyword  模糊匹配姓名/单位/职位/职级/现任职务/喜好/备注
     * @param deleted  null=全部；0=正常；1=已删除（前端仅维护人可传 1，用于恢复）
     */
    public Page<Customer> pageList(String keyword, Integer deleted, long page, long pageSize) {
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        // 软删除默认隐藏：未显式指定时只查正常记录；维护人可通过 deleted=1 查看已删除列表以便恢复
        qw.eq(Customer::getDeleted, deleted == null ? 0 : deleted);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Customer::getName, kw)
                    .or().like(Customer::getWorkUnit, kw)
                    .or().like(Customer::getWorkPosition, kw)
                    .or().like(Customer::getWorkLevel, kw)
                    .or().like(Customer::getCurrentDuty, kw)
                    .or().like(Customer::getPreferences, kw)
                    .or().like(Customer::getRemark, kw));
        }
        qw.orderByDesc(Customer::getCreateTime);
        return customerMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /**
     * 客户详情（仅正常客户；软删除客户视为不存在）。
     */
    public Customer getVisible(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null || (customer.getDeleted() != null && customer.getDeleted() == 1)) {
            throw new BusinessException("客户档案不存在或已删除");
        }
        return customer;
    }

    /**
     * 新增客户档案。如表单携带 relations，则在同事务内批量建立关联关系，任一失败整体回滚。
     */
    @Transactional
    public Customer create(CustomerForm form, AuthUser authUser) {
        validateName(form.getName());
        Customer customer = new Customer();
        applyForm(customer, form);
        customer.setDeleted(0);
        customer.setCreateBy(operator(authUser));
        customer.setUpdateBy(operator(authUser));
        customerMapper.insert(customer);
        applyRelations(customer.getId(), form.getRelations(), authUser);
        return customer;
    }

    /**
     * 批量建立"新客户 -> 已有客户"关联关系（复用 CustomerRelationService 幂等与方向逻辑）。
     * direction：NEW_TO_TARGET=新客户→已有客户（默认）；TARGET_TO_NEW=已有客户→新客户。
     */
    private void applyRelations(Long newCustomerId, List<CustomerForm.RelationItem> relations, AuthUser authUser) {
        if (relations == null || relations.isEmpty()) {
            return;
        }
        for (CustomerForm.RelationItem item : relations) {
            if (item == null) {
                continue;
            }
            if (item.getTargetCustomerId() == null) {
                throw new BusinessException("关联客户不能为空");
            }
            if (item.getTargetCustomerId().equals(newCustomerId)) {
                throw new BusinessException("不能与自己建立关系");
            }
            if (item.getRelationTypeId() == null) {
                throw new BusinessException("请选择关系类型");
            }
            boolean newToTarget = !"TARGET_TO_NEW".equals(item.getDirection());
            CustomerRelationForm rf = new CustomerRelationForm();
            rf.setFromCustomerId(newToTarget ? newCustomerId : item.getTargetCustomerId());
            rf.setToCustomerId(newToTarget ? item.getTargetCustomerId() : newCustomerId);
            rf.setRelationTypeId(item.getRelationTypeId());
            rf.setDescription(item.getDescription());
            relationService.createRelation(rf, authUser);
        }
    }

    /**
     * 编辑客户档案（仅正常客户可编辑）。
     */
    public Customer update(Long id, CustomerForm form, AuthUser authUser) {
        Customer customer = getVisible(id);
        if (StringUtils.hasText(form.getName())) {
            validateName(form.getName());
            customer.setName(form.getName().trim());
        }
        applyForm(customer, form);
        customer.setUpdateBy(operator(authUser));
        customerMapper.updateById(customer);
        return customer;
    }

    /**
     * 软删除客户档案（默认隐藏，可恢复）。
     */
    public void softDelete(Long id, AuthUser authUser) {
        Customer customer = getVisible(id);
        customer.setDeleted(1);
        customer.setDeleteBy(operator(authUser));
        customer.setDeleteTime(LocalDateTime.now());
        customer.setUpdateBy(operator(authUser));
        customerMapper.updateById(customer);
    }

    /**
     * 恢复已删除客户档案。
     */
    public void restore(Long id, AuthUser authUser) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException("客户档案不存在");
        }
        if (customer.getDeleted() == null || customer.getDeleted() != 1) {
            throw new BusinessException("该客户档案未处于删除状态");
        }
        customer.setUpdateBy(operator(authUser));
        customerMapper.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, id)
                .set(Customer::getDeleted, 0)
                .set(Customer::getDeleteBy, null)
                .set(Customer::getDeleteTime, null)
                .set(Customer::getUpdateBy, operator(authUser)));
    }

    // ==================== 附件管理 ====================

    /**
     * 客户档案附件列表。
     */
    public List<CustomerAttachment> listAttachments(Long customerId) {
        getVisible(customerId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<CustomerAttachment>()
                .eq(CustomerAttachment::getCustomerId, customerId)
                .orderByDesc(CustomerAttachment::getUploadTime));
    }

    /**
     * 上传附件（单个文件），返回附件记录。
     */
    @Transactional
    public CustomerAttachment addAttachment(Long customerId, MultipartFile file, AuthUser authUser) {
        getVisible(customerId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String storageName = storageService.saveAttachment(file);
        CustomerAttachment attachment = new CustomerAttachment();
        attachment.setCustomerId(customerId);
        attachment.setOriginalName(originalName(file.getOriginalFilename()));
        attachment.setStorageName(storageName);
        attachment.setFileType(fileType(storageName));
        attachment.setFileSize(file.getSize());
        attachment.setUploadBy(operator(authUser));
        attachmentMapper.insert(attachment);
        return attachment;
    }

    /**
     * 删除附件（记录 + 磁盘文件）。
     */
    @Transactional
    public void deleteAttachment(Long attachmentId, AuthUser authUser) {
        CustomerAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        attachmentMapper.deleteById(attachmentId);
        storageService.deleteAttachment(attachment.getStorageName());
    }

    // ==================== 私有辅助 ====================

    private void applyForm(Customer customer, CustomerForm form) {
        if (StringUtils.hasText(form.getName())) {
            customer.setName(form.getName().trim());
        }
        customer.setWorkUnit(trimToNull(form.getWorkUnit()));
        customer.setWorkPosition(trimToNull(form.getWorkPosition()));
        customer.setWorkLevel(trimToNull(form.getWorkLevel()));
        customer.setCurrentDuty(trimToNull(form.getCurrentDuty()));
        customer.setPreferences(trimToNull(form.getPreferences()));
        customer.setRemark(trimToNull(form.getRemark()));
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("客户姓名不能为空");
        }
        if (name.trim().length() > 100) {
            throw new BusinessException("客户姓名过长（≤100字）");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String originalName(String name) {
        return new java.io.File(name == null ? "" : name).getName();
    }

    private String fileType(String storageName) {
        int dot = storageName.lastIndexOf('.');
        return dot < 0 ? "" : storageName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String operator(AuthUser authUser) {
        return authUser == null ? null : authUser.getUsername();
    }
}
