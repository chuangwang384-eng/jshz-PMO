package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.mapper.ExpertMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ExpertDocumentService expertDocumentService;

    /**
     * 列表高级筛选条件（全部可选，组合生效；Owner 2026-11-07 确认口径）。
     */
    public record ExpertFilter(String unit, String department, List<String> titles,
                               String photo, String complete,
                               LocalDate updatedFrom, LocalDate updatedTo,
                               String duplicate) {
        /** 兼容旧调用（未带重名筛项）。 */
        public ExpertFilter(String unit, String department, List<String> titles,
                            String photo, String complete,
                            LocalDate updatedFrom, LocalDate updatedTo) {
            this(unit, department, titles, photo, complete, updatedFrom, updatedTo, null);
        }
    }

    /**
     * 分页列表（R6：返回 {total, items}；R5：附带下载总次数）。
     */
    public Page<Expert> pageList(String keyword, long page, long size) {
        return pageList(keyword, page, size, null);
    }

    /**
     * 分页列表 + 高级筛选（unit/department 精确、titles 多选、photo 照片状态、complete 档案完整性、更新时间范围）。
     * 筛选参数全部可选；旧调用（仅 keyword）行为不变。
     */
    public Page<Expert> pageList(String keyword, long page, long size, ExpertFilter filter) {
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
        if (filter != null) {
            if (StringUtils.hasText(filter.unit())) {
                wrapper.eq(Expert::getUnit, filter.unit().trim());
            }
            if (StringUtils.hasText(filter.department())) {
                wrapper.eq(Expert::getDepartment, filter.department().trim());
            }
            if (filter.titles() != null && !filter.titles().isEmpty()) {
                wrapper.in(Expert::getTitle, filter.titles());
            }
            if ("uploaded".equals(filter.photo())) {
                wrapper.and(w -> w.isNotNull(Expert::getPhoto).ne(Expert::getPhoto, ""));
            } else if ("missing".equals(filter.photo())) {
                wrapper.and(w -> w.isNull(Expert::getPhoto).or().eq(Expert::getPhoto, ""));
            }
            if ("complete".equals(filter.complete())) {
                wrapper.and(w -> {
                    for (var column : PROFILE_COMPLETE_COLUMNS) {
                        w.isNotNull(column).ne(column, "");
                    }
                });
            } else if ("incomplete".equals(filter.complete())) {
                wrapper.and(w -> {
                    boolean first = true;
                    for (var column : PROFILE_COMPLETE_COLUMNS) {
                        if (!first) w.or();
                        w.isNull(column).or().eq(column, "");
                        first = false;
                    }
                });
            }
            if (filter.updatedFrom() != null) {
                wrapper.ge(Expert::getUpdateTime, filter.updatedFrom().atStartOfDay());
            }
            if (filter.updatedTo() != null) {
                wrapper.lt(Expert::getUpdateTime, filter.updatedTo().plusDays(1).atStartOfDay());
            }
            if ("yes".equals(filter.duplicate())) {
                // 重名且同单位（Owner 2026-11-18）：仅保留 (name, unit) 出现 >1 次的专家
                List<Map<String, Object>> groups = this.listMaps(new QueryWrapper<Expert>()
                        .select("name", "unit")
                        .isNotNull("name").ne("name", "")
                        .isNotNull("unit").ne("unit", "")
                        .groupBy("name", "unit")
                        .having("count(*) > 1"));
                if (groups.isEmpty()) {
                    wrapper.apply("1 = 0"); // 无重复组：直接返回空集
                } else {
                    wrapper.and(w -> {
                        boolean first = true;
                        for (Map<String, Object> g : groups) {
                            if (!first) w.or();
                            w.and(i -> i.eq(Expert::getName, String.valueOf(g.get("name")))
                                    .eq(Expert::getUnit, String.valueOf(g.get("unit"))));
                            first = false;
                        }
                    });
                }
            }
        }
        wrapper.orderByDesc(Expert::getUpdateTime);
        Page<Expert> result = this.page(new Page<>(page, size), wrapper);
        Map<Long, Long> counts = downloadAuditService.countGroupByExpert();
        for (Expert e : result.getRecords()) {
            Long cnt = counts.get(e.getId());
            e.setDownloadCount(cnt == null ? 0L : cnt);
            e.setProfileComplete(isProfileComplete(e));
        }
        return result;
    }

    /** 档案完整判定列（与 isProfileComplete 口径一致：照片+身份证正反面+银行卡+联系方式）。 */
    private static final List<SFunction<Expert, ?>> PROFILE_COMPLETE_COLUMNS = List.of(
            Expert::getPhoto, Expert::getIdCardFront, Expert::getIdCardBack, Expert::getBankCard, Expert::getContact);

    /**
     * 详情（需求1：登录用户一律返回明文，full 参数保留兼容调用方；简介拼接展示见 toView）。
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

    void attachNewAvatar(Expert expert, MultipartFile photo) {
        expert.setPhoto(storeNewAvatar(expert, photo));
        if (!updateById(expert)) {
            throw new BusinessException("头像保存失败");
        }
    }

    private String storeNewAvatar(Expert expert, MultipartFile photo) {
        String filename = photoStorageService.savePhoto(photo, expert.getId(), "photo");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        photoStorageService.deletePhoto(filename);
                    }
                }
            });
        }
        return filename;
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
        // 级联删除资质资料（登记行+物理文件，2026-11-20）
        expertDocumentService.deleteByExpert(id);
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
     * 视图转换：身份证号/银行卡号一律返回明文（需求1：去除脱敏，全员明文）；
     * 简介按"原简介 + 研究方向 + 擅长领域"拼接展示（需求2），同时保留原始简介供编辑回填。
     */
    public Expert toView(Expert expert, boolean full) {
        if (expert == null) {
            return null;
        }
        // 保留原始简介（数据库原值），编辑弹窗回填用，避免把拼接值写回存储
        expert.setRawProfile(expert.getProfile());
        expert.setProfile(mergeProfile(expert.getProfile(), expert.getResearchDirection(), expert.getExpertiseArea()));
        expert.setProfileComplete(isProfileComplete(expert));
        return expert;
    }

    /** 档案完整 = 照片、身份证正反面、银行卡、联系方式均非空（Owner 2026-11-07 定义口径）。 */
    public static boolean isProfileComplete(Expert e) {
        return e != null && hasText(e.getPhoto()) && hasText(e.getIdCardFront())
                && hasText(e.getIdCardBack()) && hasText(e.getBankCard()) && hasText(e.getContact());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 高级筛选字典：单位/科室/职称去重（登录即可），按出现次数降序。
     */
    public Map<String, List<String>> filterOptions() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("units", distinctValues("unit"));
        result.put("departments", distinctValues("department"));
        result.put("titles", distinctValues("title"));
        return result;
    }

    private List<String> distinctValues(String column) {
        List<Map<String, Object>> rows = this.listMaps(new QueryWrapper<Expert>()
                .select(column + " AS v, COUNT(*) AS cnt")
                .isNotNull(column).ne(column, "")
                .groupBy(column)
                .orderByDesc("cnt"));
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object v = row.get("v");
            if (v != null && StringUtils.hasText(String.valueOf(v))) {
                values.add(String.valueOf(v));
            }
        }
        return values;
    }

    /**
     * 合并简介（需求2）：原简介 + 研究方向 + 擅长领域，字段有内容才拼接，避免多余分隔符。
     * 幂等保护：若原简介已包含当前研究方向/擅长领域片段（如员工编辑回写拼接值），直接返回原值，
     * 防止重复拼接。示例："从事会展经济研究。研究方向：会展产业政策。擅长领域：数据分析。"
     */
    public static String mergeProfile(String profile, String researchDirection, String expertiseArea) {
        String p = StringUtils.hasText(profile) ? profile.trim() : "";
        String rd = StringUtils.hasText(researchDirection) ? researchDirection.trim() : null;
        String ea = StringUtils.hasText(expertiseArea) ? expertiseArea.trim() : null;
        if (rd != null && p.contains("研究方向：" + rd)
                && (ea == null || p.contains("擅长领域：" + ea))) {
            return p;
        }
        StringBuilder sb = new StringBuilder(p);
        if (rd != null) {
            appendProfilePart(sb, "研究方向：" + rd);
        }
        if (ea != null) {
            appendProfilePart(sb, "擅长领域：" + ea);
        }
        return sb.toString();
    }

    /** 追加简介片段：清理前段结尾多余空白/句号后以"。"衔接，避免重复标点。 */
    private static void appendProfilePart(StringBuilder sb, String part) {
        if (sb.length() == 0) {
            sb.append(part);
            return;
        }
        int len = sb.length();
        while (len > 0 && (sb.charAt(len - 1) == '。' || sb.charAt(len - 1) == ' '
                || sb.charAt(len - 1) == '\n' || sb.charAt(len - 1) == '\r' || sb.charAt(len - 1) == '\t')) {
            len--;
        }
        sb.setLength(len);
        sb.append('。').append(part);
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
            expert.setPhoto(storeNewAvatar(expert, photo));
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
