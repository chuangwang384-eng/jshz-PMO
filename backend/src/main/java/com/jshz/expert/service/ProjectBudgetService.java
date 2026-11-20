package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectBudgetFeeStandard;
import com.jshz.expert.entity.ProjectBudgetItem;
import com.jshz.expert.mapper.ProjectBudgetFeeStandardMapper;
import com.jshz.expert.mapper.ProjectBudgetItemMapper;
import com.jshz.expert.mapper.ProjectBudgetMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会议预算制作（2026-11-16 新阶段，位于日程上传之前）。
 * 线下：六类费用明细（酒店/交通/搭建/物料/媒体/礼品/人员）；线上：简化报价单（模板点选形式待定）。
 * 收费默认值取 project_budget_fee_standard（管理员可改），首次打开按默认骨架预填，
 * 保存整单覆盖式、金额服务端计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBudgetService {

    private final ProjectBudgetMapper budgetMapper;
    private final ProjectBudgetItemMapper itemMapper;
    private final ProjectBudgetFeeStandardMapper feeStandardMapper;
    private final ProjectService projectService;

    /** 明细分节合法值（与 project_budget_item.section 注释一致） */
    private static final List<String> SECTIONS = List.of(
            "HOTEL", "TRAFFIC", "BUILD", "MATERIAL", "MEDIA", "GIFT", "STAFF", "ONLINE_QUOTE");

    // ==================== 预算单 ====================

    /** 预算单总览：不存在则按项目大类初始化骨架（不落库，草稿保存时才落库）。 */
    public Map<String, Object> overview(Long projectId, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireProjectReadable(project, user);
        ProjectBudget budget = budgetMapper.selectOne(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getProjectId, projectId));
        List<ProjectBudgetItem> items;
        if (budget == null) {
            budget = new ProjectBudget();
            budget.setProjectId(projectId);
            budget.setCategory(categoryOf(project));
            budget.setStatus("DRAFT");
            budget.setTotalAmount(BigDecimal.ZERO);
            items = skeleton(budget);
        } else {
            items = itemMapper.selectList(new LambdaQueryWrapper<ProjectBudgetItem>()
                    .eq(ProjectBudgetItem::getBudgetId, budget.getId())
                    .orderByAsc(ProjectBudgetItem::getSort));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("budget", budget);
        result.put("items", items);
        result.put("feeStandards", activeFeeStandards(budget.getCategory()));
        return result;
    }

    /** 整单覆盖式保存（草稿/确认同一入口，confirm=true 时置 CONFIRMED）。 */
    @Transactional
    public Map<String, Object> save(Long projectId, List<Map<String, Object>> items,
                                    String remark, boolean confirm, AuthUser user) {
        return save(projectId, items, remark, confirm, null, user);
    }

    @Transactional
    public Map<String, Object> save(Long projectId, List<Map<String, Object>> items,
                                    String remark, boolean confirm, String estimateInputs, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        ProjectBudget budget = lockBudget(projectId);
        if (budget == null) {
            budget = new ProjectBudget();
            budget.setProjectId(projectId);
            budget.setCreatedBy(user == null ? null : user.getUserId());
        }
        if ("CONFIRMED".equals(budget.getStatus())) {
            throw new BusinessException("报价单已确认，如需修改请从对客报价版本新建草稿");
        }
        budget.setCategory(categoryOf(project));
        if ("OFFLINE".equals(budget.getCategory()) && estimateInputs != null) {
            budget.setEstimateInputs(estimateInputs);
        }
        budget.setRemark(remark);
        budget.setUpdatedBy(user == null ? null : user.getUserId());

        List<ProjectBudgetItem> parsed = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        int sort = 0;
        for (Map<String, Object> raw : items == null ? List.<Map<String, Object>>of() : items) {
            ProjectBudgetItem it = parseItem(raw, budget, sort++);
            if (it == null) {
                continue;
            }
            total = total.add(it.getAmount());
            costTotal = costTotal.add(it.getCostAmount());
            parsed.add(it);
        }
        budget.setTotalAmount(total);
        budget.setCostTotal(costTotal);
        if (budget.getServiceRate() == null) budget.setServiceRate(new BigDecimal("0.06"));
        if (budget.getTaxRate() == null) budget.setTaxRate(new BigDecimal("0.06"));
        budget.setStatus(confirm ? "CONFIRMED" : "DRAFT");
        if (budget.getId() == null) {
            budgetMapper.insert(budget);
        } else {
            budgetMapper.updateById(budget);
        }
        // 整单覆盖：删旧插新（同表小事务，项目级唯一）
        itemMapper.delete(new LambdaQueryWrapper<ProjectBudgetItem>()
                .eq(ProjectBudgetItem::getBudgetId, budget.getId()));
        for (ProjectBudgetItem it : parsed) {
            it.setBudgetId(budget.getId());
            it.setProjectId(projectId);
            itemMapper.insert(it);
        }
        log.info("预算保存 project={} items={} total={} confirm={} by={}",
                projectId, parsed.size(), total, confirm, user == null ? null : user.getUserId());
        return savedBudget(budget);
    }

    /** 确认已保存的草稿，保留原始金额、备注和明细。 */
    @Transactional
    public Map<String, Object> confirm(Long projectId, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        ProjectBudget budget = lockBudget(projectId);
        if (budget == null) {
            throw new BusinessException("请先保存报价草稿");
        }
        if ("DRAFT".equals(budget.getStatus())) {
            ProjectBudget update = new ProjectBudget();
            update.setId(budget.getId());
            update.setStatus("CONFIRMED");
            update.setUpdatedBy(user.getUserId());
            budgetMapper.updateById(update);
            budget = lockBudget(projectId);
            log.info("预算确认 project={} by={}", projectId, user.getUserId());
        }
        // A16/D3（2026-11-17 负责人拍板）：以会议预算合计为准，确认时覆盖项目财务登记金额（幂等，重复确认/已确认单再确认均同步）
        projectService.overrideBudgetAmount(projectId, budget.getTotalAmount());
        return savedBudget(budget);
    }

    /** 保存和确认共用行锁，避免草稿写入覆盖刚确认的状态。 */
    ProjectBudget lockBudget(Long projectId) {
        return budgetMapper.selectOne(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getProjectId, projectId)
                .last("FOR UPDATE"));
    }

    private Map<String, Object> savedBudget(ProjectBudget budget) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("budget", budget);
        result.put("items", lockedItems(budget.getId()));
        return result;
    }

    List<ProjectBudgetItem> lockedItems(Long budgetId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ProjectBudgetItem>()
                .eq(ProjectBudgetItem::getBudgetId, budgetId)
                .orderByAsc(ProjectBudgetItem::getSort)
                .last("FOR UPDATE"));
    }

    // ==================== 收费标准（管理员） ====================

    public List<ProjectBudgetFeeStandard> listFeeStandards(String scene) {
        LambdaQueryWrapper<ProjectBudgetFeeStandard> q = new LambdaQueryWrapper<ProjectBudgetFeeStandard>();
        if (scene != null && !scene.isBlank()) {
            q.eq(ProjectBudgetFeeStandard::getScene, scene);
        }
        return feeStandardMapper.selectList(q.orderByAsc(ProjectBudgetFeeStandard::getId));
    }

    /** 新增/修改收费标准（fee_key 唯一；只允许新增键或改值，不提供删除以防预算引用悬空）。 */
    @Transactional
    public List<ProjectBudgetFeeStandard> saveFeeStandards(List<Map<String, Object>> rows, AuthUser user) {
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String key = str(row.get("feeKey"));
            String name = str(row.get("name"));
            if (key == null || key.isBlank() || name == null || name.isBlank()) {
                throw new BusinessException("收费标准 fee_key 与名称不能为空");
            }
            if (!key.matches("[A-Z0-9_]{1,50}")) {
                throw new BusinessException("fee_key 仅允许大写字母/数字/下划线：" + key);
            }
            String value = str(row.get("defaultValue"));
            if (value != null && !value.isBlank() && !value.matches("\\d{1,10}(\\.\\d{1,2})?(,\\d{1,10}(\\.\\d{1,2})?)?")) {
                throw new BusinessException("默认值须为数字或 \"低,高\" 区间：" + value);
            }
            String scene = str(row.get("scene"));
            if (scene == null || !("ONLINE".equals(scene) || "OFFLINE".equals(scene))) {
                scene = "OFFLINE";
            }
            if ("OFFLINE".equals(scene) && List.of("AGENDA_UP", "MAT_DESKCARD_UP", "MAT_STAND").contains(key)
                    && value != null && !value.matches("\\d{1,4}(,\\d{1,4})?")) {
                throw new BusinessException("物料数量规则须为整数或整数区间");
            }
            if (value != null && value.contains(",")) {
                String[] bounds = value.split(",");
                if (new BigDecimal(bounds[0]).compareTo(new BigDecimal(bounds[1])) > 0) {
                    throw new BusinessException("默认值区间的下限不能大于上限");
                }
            }
            ProjectBudgetFeeStandard entity = feeStandardMapper.selectOne(
                    new LambdaQueryWrapper<ProjectBudgetFeeStandard>().eq(ProjectBudgetFeeStandard::getFeeKey, key));
            boolean exists = entity != null;
            if (!exists) {
                entity = new ProjectBudgetFeeStandard();
                entity.setFeeKey(key);
            }
            entity.setName(name);
            entity.setScene(scene);
            // 收费标准库扩列（2026-11-20）：类目/小类/详细内容/规格/成本价/最低利润
            entity.setCatalog(str(row.get("catalog")));
            entity.setCategory(str(row.get("category")));
            entity.setDetail(str(row.get("detail")));
            entity.setSpec(str(row.get("spec")));
            BigDecimal costPrice = decimal(row.get("costPrice"), "成本价");
            if (costPrice.signum() < 0) {
                throw new BusinessException("成本价不能为负数");
            }
            entity.setCostPrice(costPrice.signum() == 0 ? null : costPrice);
            BigDecimal minProfit = decimal(row.get("minProfit"), "最低利润");
            if (minProfit.signum() < 0) {
                throw new BusinessException("最低利润不能为负数");
            }
            entity.setMinProfit(minProfit.signum() == 0 ? null : minProfit);
            entity.setDefaultValue(value == null || value.isBlank() ? null : value);
            entity.setUnit(str(row.get("unit")));
            entity.setRemark(str(row.get("remark")));
            Object active = row.get("isActive");
            entity.setIsActive(active == null || "1".equals(String.valueOf(active)) || Boolean.parseBoolean(String.valueOf(active)) ? 1 : 0);
            if (exists) {
                feeStandardMapper.updateById(entity);
            } else {
                feeStandardMapper.insert(entity);
            }
        }
        log.info("收费标准保存 {} 行 by={}", rows == null ? 0 : rows.size(), user == null ? null : user.getUserId());
        return listFeeStandards(null);
    }

    // ==================== 内部 ====================

    private String categoryOf(Project project) {
        String c = project.getCategory();
        if (c == null || c.isBlank()) {
            return "OFFLINE";
        }
        return "ONLINE".equalsIgnoreCase(c) ? "ONLINE" : "OFFLINE";
    }

    private Map<String, ProjectBudgetFeeStandard> activeFeeStandards() {
        return activeFeeStandards(null);
    }

    /** 按场景取启用收费标准（scene=null 全量；否则 ONLINE/OFFLINE）。 */
    private Map<String, ProjectBudgetFeeStandard> activeFeeStandards(String scene) {
        Map<String, ProjectBudgetFeeStandard> map = new LinkedHashMap<>();
        LambdaQueryWrapper<ProjectBudgetFeeStandard> q = new LambdaQueryWrapper<ProjectBudgetFeeStandard>()
                .eq(ProjectBudgetFeeStandard::getIsActive, 1);
        if (scene != null) {
            q.eq(ProjectBudgetFeeStandard::getScene, scene);
        }
        for (ProjectBudgetFeeStandard s : feeStandardMapper.selectList(q)) {
            map.put(s.getFeeKey(), s);
        }
        return map;
    }

    /** 校验并计算一行明细；name 为空视为用户删掉的空行，返回 null 跳过。 */
    private ProjectBudgetItem parseItem(Map<String, Object> raw, ProjectBudget budget, int sort) {
        if (raw == null) {
            return null;
        }
        String name = str(raw.get("name"));
        if (name == null || name.isBlank()) {
            return null;
        }
        String section = str(raw.get("section"));
        if (section == null || !SECTIONS.contains(section)) {
            throw new BusinessException("未知费用分节：" + section);
        }
        if (!"ONLINE".equals(budget.getCategory()) && "ONLINE_QUOTE".equals(section)) {
            throw new BusinessException("线下项目不允许线上报价分节");
        }
        if ("ONLINE".equals(budget.getCategory()) && !"ONLINE_QUOTE".equals(section)) {
            throw new BusinessException("线上项目仅允许简化报价单分节");
        }
        ProjectBudgetItem it = new ProjectBudgetItem();
        it.setSection(section);
        it.setItemKey(str(raw.get("itemKey")));
        it.setName(name.trim());
        BigDecimal qty = decimal(raw.get("quantity"), "数量");
        BigDecimal price = decimal(raw.get("unitPrice"), "单价");
        if (qty.signum() < 0 || price.signum() < 0) {
            throw new BusinessException("数量/单价不能为负数");
        }
        it.setQuantity(qty);
        it.setUnit(str(raw.get("unit")));
        it.setUnitPrice(price);
        it.setAmount(qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP));
        // 成本单维度（2026-11-20）：成本价可空（按需报价项），金额服务端计算
        BigDecimal cost = decimal(raw.get("costPrice"), "成本单价");
        if (cost.signum() < 0) {
            throw new BusinessException("成本单价不能为负数");
        }
        it.setCostPrice(cost);
        it.setCostAmount(qty.multiply(cost).setScale(2, java.math.RoundingMode.HALF_UP));
        it.setRemark(str(raw.get("remark")));
        it.setSort(sort);
        return it;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimal(Object o, String label) {
        if (o == null || String.valueOf(o).isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(label + "格式不正确：" + o);
        }
    }

    /**
     * 按大类生成骨架明细（不落库）。OFFLINE 七节预填固定项 + 收费标准默认值；
     * ONLINE 一行空报价。区间默认值（如 300,400）取低值预填，备注保留区间说明。
     */
    private List<ProjectBudgetItem> skeleton(ProjectBudget budget) {
        Map<String, ProjectBudgetFeeStandard> fees = activeFeeStandards();
        List<ProjectBudgetItem> list = new ArrayList<>();
        int sort = 0;
        if ("ONLINE".equals(budget.getCategory())) {
            // 线上=点选收费项生成报价单：骨架列出全部启用线上收费项（未选中 quantity 置 0），
            // 前端勾选即计入合计；无任何线上收费项时保底一行空报价。
            Map<String, ProjectBudgetFeeStandard> onlineFees = activeFeeStandards("ONLINE");
            if (onlineFees.isEmpty()) {
                list.add(item(budget, "ONLINE_QUOTE", null, "报价项", null, null, sort++));
                return list;
            }
            for (ProjectBudgetFeeStandard std : onlineFees.values()) {
                ProjectBudgetItem it = feeItem(budget, onlineFees, std.getFeeKey(), std.getName(), null, "ONLINE_QUOTE", sort++);
                if (it == null) {
                    continue;
                }
                it.setQuantity(BigDecimal.ZERO); // 默认未选
                it.setAmount(BigDecimal.ZERO);
                list.add(it);
            }
            return list;
        }
        // HOTEL 酒店：场地/住宿/用餐/茶歇
        list.add(item(budget, "HOTEL", null, "场地费", null, null, sort++));
        list.add(estimateItem(budget, "HOTEL", "HOTEL_ROOM", "住宿费", "间", sort++));
        list.add(item(budget, "HOTEL", null, "用餐费", null, null, sort++));
        list.add(estimateItem(budget, "HOTEL", "HOTEL_TEA", "茶歇费", "人", sort++));
        // TRAFFIC 交通：大交通按专家人数预估 + 地面交通（车队专车/平台专车）
        list.add(estimateItem(budget, "TRAFFIC", "TRAFFIC_MAIN", "大交通费（按专家人数预估）", "人", sort++));
        list.add(estimateItem(budget, "TRAFFIC", "TRAFFIC_FLEET", "地面交通-车队专车", "次", sort++));
        list.add(estimateItem(budget, "TRAFFIC", "TRAFFIC_PLATFORM", "地面交通-平台专车", "人", sort++));
        // BUILD 搭建（非必需）：签到背景板/LED 屏/影音设备
        list.add(item(budget, "BUILD", null, "签到背景板", null, null, sort++));
        list.add(item(budget, "BUILD", null, "LED 屏幕", null, null, sort++));
        list.add(item(budget, "BUILD", null, "影音设备", null, null, sort++));
        // MATERIAL 物料：设计费 + 公司收取（串场/沟通函及延展）+ 明细项（默认值取收费标准）
        list.add(item(budget, "MATERIAL", "MAT_DESIGN", "物料设计费", null, null, sort++));
        list.add(feeItem(budget, fees, "MC_INTERLUDE", "串场", BigDecimal.ONE, "MATERIAL", sort++));
        list.add(feeItem(budget, fees, "MC_LETTER", "沟通函及延展", BigDecimal.ONE, "MATERIAL", sort++));
        list.add(materialQuantityItem(budget, fees, "AGENDA_UP", "日程", "张", sort++));
        list.add(materialQuantityItem(budget, fees, "MAT_DESKCARD_UP", "台卡", "张", sort++));
        list.add(materialQuantityItem(budget, fees, "MAT_STAND", "展架（易拉宝）", "个", sort++));
        list.add(item(budget, "MATERIAL", "MAT_BANNER", "条幅（按酒店尺寸，默认写真布）", null, null, sort++));
        list.add(feeItem(budget, fees, "MAT_PRINT_OTHER", "其他打印物料", BigDecimal.ONE, "MATERIAL", sort++));
        list.add(feeItem(budget, fees, "MAT_ROSTRUM_FLOWER", "讲台花", BigDecimal.ONE, "MATERIAL", sort++));
        list.add(item(budget, "MATERIAL", "MAT_FLOWER_OTHER", "桌花及其他花束（视需求报价）", null, null, sort++));
        // MEDIA 媒体（非必需）
        list.add(item(budget, "MEDIA", null, "媒体费", null, null, sort++));
        // GIFT 礼品（和客户确认是否需要）
        list.add(feeItem(budget, fees, "GIFT_UNIT", "礼品", null, "GIFT", sort++));
        // STAFF 人员：固定 500/人/天 + 交通住宿据实报销（说明行）
        ProjectBudgetItem staff = feeItem(budget, fees, "STAFF_DAY", "人员费", BigDecimal.ZERO, "STAFF", sort++);
        if (staff != null) {
            staff.setUnit("人天");
            list.add(staff);
        }
        list.add(item(budget, "STAFF", "STAFF_TRAVEL", "人员交通/住宿（据实报销）", null, null, sort++));
        return list.stream().filter(Objects::nonNull).toList();
    }

    private ProjectBudgetItem item(ProjectBudget budget, String section, String key, String name,
                                   BigDecimal price, String unit, int sort) {
        ProjectBudgetItem it = new ProjectBudgetItem();
        it.setSection(section);
        it.setItemKey(key);
        it.setName(name);
        it.setQuantity(BigDecimal.ONE);
        it.setUnitPrice(price == null ? BigDecimal.ZERO : price);
        it.setAmount(it.getQuantity().multiply(it.getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP));
        it.setUnit(unit);
        it.setSort(sort);
        return it;
    }

    private ProjectBudgetItem materialQuantityItem(ProjectBudget budget, Map<String, ProjectBudgetFeeStandard> fees,
                                                   String key, String name, String unit, int sort) {
        ProjectBudgetFeeStandard standard = fees.get(key);
        if (standard == null) {
            return null;
        }
        return estimateItem(budget, "MATERIAL", key, name, unit, sort);
    }

    private ProjectBudgetItem estimateItem(ProjectBudget budget, String section, String key,
                                           String name, String unit, int sort) {
        ProjectBudgetItem row = item(budget, section, key, name, BigDecimal.ZERO, unit, sort);
        row.setQuantity(BigDecimal.ZERO);
        return row;
    }

    /** 收费标准预填行；标准缺失（被停用/删除）返回 null 跳过。区间默认取低值并备注区间。 */
    private ProjectBudgetItem feeItem(ProjectBudget budget, Map<String, ProjectBudgetFeeStandard> fees,
                                      String feeKey, String defaultName, BigDecimal qty, String section, int sort) {
        ProjectBudgetFeeStandard std = fees.get(feeKey);
        if (std == null) {
            return null;
        }
        BigDecimal price = BigDecimal.ZERO;
        String remark = std.getRemark();
        String value = std.getDefaultValue();
        if (value != null && !value.isBlank()) {
            String low = value.split(",")[0].trim();
            price = new BigDecimal(low);
            if (value.contains(",")) {
                remark = (remark == null ? "" : remark + "；") + "区间 " + value.replace(",", "-") + "，默认取低值";
            }
        }
        ProjectBudgetItem it = item(budget, section, feeKey, std.getName() != null ? std.getName() : defaultName,
                price, std.getUnit(), sort);
        if (qty != null) {
            it.setQuantity(qty);
            it.setAmount(it.getQuantity().multiply(it.getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        it.setRemark(remark);
        return it;
    }
}
