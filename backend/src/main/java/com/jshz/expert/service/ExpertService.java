package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.mapper.ExpertMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

/**
 * 专家信息服务（正式版 V1.001）。
 * 18 字段 CRUD、脱敏（前3后4）、照片三类物理维护、下载次数统计。
 */
@Service
@RequiredArgsConstructor
public class ExpertService extends ServiceImpl<ExpertMapper, Expert> {

    private final PhotoStorageService photoStorageService;
    private final DownloadAuditService downloadAuditService;

    /**
     * 分页列表（R6：返回 {total, items}；R5：附带下载总次数）。
     */
    public Page<Expert> pageList(String keyword, long page, long size) {
        LambdaQueryWrapper<Expert> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Expert::getName, kw)
                    .or().like(Expert::getUnit, kw)
                    .or().like(Expert::getDepartment, kw)
                    .or().like(Expert::getTitle, kw)
                    .or().like(Expert::getResearchDirection, kw)
                    .or().like(Expert::getExpertiseArea, kw)
                    .or().like(Expert::getContact, kw)
                    .or().like(Expert::getProfile, kw));
        }
        wrapper.orderByDesc(Expert::getUpdateTime);
        Page<Expert> result = this.page(new Page<>(page, size), wrapper);
        Map<Long, Long> counts = downloadAuditService.countGroupByExpert();
        for (Expert e : result.getRecords()) {
            Long cnt = counts.get(e.getId());
            e.setDownloadCount(cnt == null ? 0L : cnt);
        }
        return result;
    }

    /**
     * 详情（脱敏规则：full=true 全量，否则身份证/银行卡前3后4）。
     */
    public Expert detail(Long id, boolean full) {
        Expert expert = getById(id);
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
        expert.setDownloadCount(downloadAuditService.countByExpertId(id));
        return toView(expert, full);
    }

    /**
     * 新增（JSON 场景）。
     */
    public Expert create(Expert expert, AuthUser user) {
        validateRequired(expert);
        expert.setId(null);
        expert.setUpdateBy(user == null ? null : user.getUsername());
        this.save(expert);
        return expert;
    }

    /**
     * 新增（multipart 场景，支持三类照片文件字段：photo / idCardFront / idCardBack）。
     */
    public Expert createFromMultipart(MultipartHttpServletRequest request, AuthUser user) {
        Expert expert = buildFromParams(request);
        validateRequired(expert);
        expert.setId(null);
        expert.setUpdateBy(user == null ? null : user.getUsername());
        this.save(expert);
        savePhotos(expert, request);
        return getById(expert.getId());
    }

    /**
     * 修改（JSON 场景）。
     */
    public Expert update(Long id, Expert patch, AuthUser user) {
        if (getById(id) == null) {
            throw new BusinessException("专家不存在");
        }
        patch.setId(id);
        patch.setCreateTime(null); // 防止覆盖创建时间
        if (user != null) {
            patch.setUpdateBy(user.getUsername());
        }
        this.updateById(patch);
        return getById(id);
    }

    /**
     * 修改（multipart 场景，换照片时删除旧文件）。
     */
    public Expert updateFromMultipart(Long id, MultipartHttpServletRequest request, AuthUser user) {
        Expert existing = getById(id);
        if (existing == null) {
            throw new BusinessException("专家不存在");
        }
        Expert patch = buildFromParams(request);
        patch.setId(id);
        patch.setCreateTime(null);
        if (user != null) {
            patch.setUpdateBy(user.getUsername());
        }
        this.updateById(patch);

        Expert updated = getById(id);
        boolean photoChanged = false;
        MultipartFile photo = request.getFile("photo");
        if (photo != null && !photo.isEmpty()) {
            String old = updated.getPhoto();
            updated.setPhoto(photoStorageService.savePhoto(photo, id, "photo"));
            if (StringUtils.hasText(old) && !old.equals(updated.getPhoto())) {
                photoStorageService.deletePhoto(old);
            }
            photoChanged = true;
        }
        MultipartFile front = request.getFile("idCardFront");
        if (front != null && !front.isEmpty()) {
            String old = updated.getIdCardFront();
            updated.setIdCardFront(photoStorageService.savePhoto(front, id, "idcard_front"));
            if (StringUtils.hasText(old) && !old.equals(updated.getIdCardFront())) {
                photoStorageService.deletePhoto(old);
            }
            photoChanged = true;
        }
        MultipartFile back = request.getFile("idCardBack");
        if (back != null && !back.isEmpty()) {
            String old = updated.getIdCardBack();
            updated.setIdCardBack(photoStorageService.savePhoto(back, id, "idcard_back"));
            if (StringUtils.hasText(old) && !old.equals(updated.getIdCardBack())) {
                photoStorageService.deletePhoto(old);
            }
            photoChanged = true;
        }
        if (photoChanged) {
            this.updateById(updated);
        }
        return updated;
    }

    /**
     * 删除专家并物理删除三类照片（R6）。
     */
    public void deleteExpert(Long id) {
        Expert expert = getById(id);
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
        if (StringUtils.hasText(expert.getPhoto())) {
            photoStorageService.deletePhoto(expert.getPhoto());
        }
        if (StringUtils.hasText(expert.getIdCardFront())) {
            photoStorageService.deletePhoto(expert.getIdCardFront());
        }
        if (StringUtils.hasText(expert.getIdCardBack())) {
            photoStorageService.deletePhoto(expert.getIdCardBack());
        }
        this.removeById(id);
    }

    /**
     * 脱敏（前3后4）。长度不超过 7 时原样返回。
     */
    public static String maskSensitive(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String s = value.trim();
        if (s.length() <= 7) {
            return s;
        }
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    /**
     * 视图转换：full=false 时复制实体并对身份证/银行卡脱敏。
     */
    public Expert toView(Expert expert, boolean full) {
        if (expert == null) {
            return null;
        }
        if (full) {
            return expert;
        }
        Expert view = new Expert();
        BeanUtils.copyProperties(expert, view);
        view.setIdCard(maskSensitive(expert.getIdCard()));
        view.setBankCard(maskSensitive(expert.getBankCard()));
        return view;
    }

    private void validateRequired(Expert expert) {
        if (expert == null || !StringUtils.hasText(expert.getName())) {
            throw new BusinessException("姓名不能为空");
        }
        if (StringUtils.hasText(expert.getIdCard())
                && !expert.getIdCard().matches("^\\d{17}[\\dXx]$")) {
            throw new BusinessException("身份证号格式不正确（18 位，末位可为 X）");
        }
        if (StringUtils.hasText(expert.getBankCard())
                && !expert.getBankCard().matches("^\\d{13,19}$")) {
            throw new BusinessException("银行卡号格式不正确（13-19 位数字）");
        }
        if (StringUtils.hasText(expert.getContact())
                && !expert.getContact().matches("^1\\d{10}$|^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new BusinessException("联系方式格式不正确（11 位手机号或邮箱）");
        }
    }

    /**
     * 从 multipart 表单读取中文字段（与接口中文键一致）。
     */
    private Expert buildFromParams(MultipartHttpServletRequest request) {
        Expert expert = new Expert();
        expert.setName(request.getParameter("姓名"));
        expert.setUnit(request.getParameter("单位"));
        expert.setDepartment(request.getParameter("科室"));
        expert.setTitle(request.getParameter("职称"));
        expert.setResearchDirection(request.getParameter("研究方向"));
        expert.setExpertiseArea(request.getParameter("擅长领域"));
        expert.setContact(request.getParameter("联系方式"));
        expert.setProfile(request.getParameter("简介"));
        expert.setIdCard(request.getParameter("身份证号"));
        expert.setBankCard(request.getParameter("银行卡号"));
        expert.setBankBranch(request.getParameter("银行卡开户行"));
        return expert;
    }

    private void savePhotos(Expert expert, MultipartHttpServletRequest request) {
        boolean changed = false;
        MultipartFile photo = request.getFile("photo");
        if (photo != null && !photo.isEmpty()) {
            expert.setPhoto(photoStorageService.savePhoto(photo, expert.getId(), "photo"));
            changed = true;
        }
        MultipartFile front = request.getFile("idCardFront");
        if (front != null && !front.isEmpty()) {
            expert.setIdCardFront(photoStorageService.savePhoto(front, expert.getId(), "idcard_front"));
            changed = true;
        }
        MultipartFile back = request.getFile("idCardBack");
        if (back != null && !back.isEmpty()) {
            expert.setIdCardBack(photoStorageService.savePhoto(back, expert.getId(), "idcard_back"));
            changed = true;
        }
        if (changed) {
            this.updateById(expert);
        }
    }
}
