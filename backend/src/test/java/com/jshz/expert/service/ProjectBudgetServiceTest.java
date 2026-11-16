package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectBudgetFeeStandard;
import com.jshz.expert.entity.ProjectBudgetItem;
import com.jshz.expert.mapper.ProjectBudgetFeeStandardMapper;
import com.jshz.expert.mapper.ProjectBudgetItemMapper;
import com.jshz.expert.mapper.ProjectBudgetMapper;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 会议预算：骨架初始化、金额服务端计算、分节/大类校验、收费标准校验。
 */
@ExtendWith(MockitoExtension.class)
class ProjectBudgetServiceTest {

    @Mock ProjectBudgetMapper budgetMapper;
    @Mock ProjectBudgetItemMapper itemMapper;
    @Mock ProjectBudgetFeeStandardMapper feeStandardMapper;
    @Mock ProjectService projectService;
    @InjectMocks ProjectBudgetService service;

    private final AuthUser user = new AuthUser(7L, "tester", false, false, java.util.Set.of(), java.util.Set.of(), true);

    private Project project(String category) {
        Project p = new Project();
        p.setId(1L);
        p.setCategory(category);
        return p;
    }

    private ProjectBudgetFeeStandard fee(String key, String value, String unit) {
        ProjectBudgetFeeStandard s = new ProjectBudgetFeeStandard();
        s.setFeeKey(key);
        s.setName(key);
        s.setDefaultValue(value);
        s.setUnit(unit);
        s.setIsActive(1);
        return s;
    }

    @Test
    void offlineSkeletonPrefillsFeeStandardsWithLowBoundOfRange() {
        when(projectService.requireProject(1L)).thenReturn(project("OFFLINE"));
        when(budgetMapper.selectOne(any())).thenReturn(null);
        when(feeStandardMapper.selectList(any())).thenReturn(List.of(
                fee("MC_INTERLUDE", "500", "元/场"),
                fee("MAT_ROSTRUM_FLOWER", "300,400", "元"),
                fee("STAFF_DAY", "500", "元/人/天"),
                fee("GIFT_UNIT", "200,300", "元/份")));

        Map<String, Object> result = service.overview(1L, user);
        @SuppressWarnings("unchecked")
        List<ProjectBudgetItem> items = (List<ProjectBudgetItem>) result.get("items");

        assertThat(items).extracting(ProjectBudgetItem::getSection).contains("HOTEL", "TRAFFIC", "BUILD", "MATERIAL", "MEDIA", "GIFT", "STAFF");
        ProjectBudgetItem mc = items.stream().filter(i -> "MC_INTERLUDE".equals(i.getItemKey())).findFirst().orElseThrow();
        assertThat(mc.getUnitPrice()).isEqualByComparingTo("500");
        ProjectBudgetItem flower = items.stream().filter(i -> "MAT_ROSTRUM_FLOWER".equals(i.getItemKey())).findFirst().orElseThrow();
        assertThat(flower.getUnitPrice()).isEqualByComparingTo("300"); // 区间取低值
        assertThat(flower.getRemark()).contains("300-400");
        ProjectBudgetItem staff = items.stream().filter(i -> "STAFF_DAY".equals(i.getItemKey())).findFirst().orElseThrow();
        assertThat(staff.getUnitPrice()).isEqualByComparingTo("500");
    }

    @Test
    void onlineSkeletonIsSingleQuoteSection() {
        when(projectService.requireProject(1L)).thenReturn(project("ONLINE"));
        when(budgetMapper.selectOne(any())).thenReturn(null);
        when(feeStandardMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.overview(1L, user);
        @SuppressWarnings("unchecked")
        List<ProjectBudgetItem> items = (List<ProjectBudgetItem>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSection()).isEqualTo("ONLINE_QUOTE");
    }

    @Test
    void saveComputesAmountAndTotalServerSide() {
        when(projectService.requireProject(1L)).thenReturn(project("OFFLINE"));
        when(budgetMapper.selectOne(any())).thenReturn(null);
        when(budgetMapper.insert(any(ProjectBudget.class))).thenAnswer(inv -> {
            inv.getArgument(0, ProjectBudget.class).setId(10L);
            return 1;
        });
        lenient().when(feeStandardMapper.selectList(any())).thenReturn(List.of());
        // 回读口径：捕获插入的明细行并作为 selectList 结果返回
        java.util.List<ProjectBudgetItem> inserted = new java.util.ArrayList<>();
        when(itemMapper.insert(any(ProjectBudgetItem.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0));
            return 1;
        });
        when(itemMapper.selectList(any())).thenReturn(inserted);

        Map<String, Object> saved = service.save(1L, List.of(
                Map.of("section", "HOTEL", "name", "场地费", "quantity", 2, "unitPrice", 1500.5),
                Map.of("section", "STAFF", "name", "人员费", "quantity", 3, "unitPrice", 500),
                Map.of("section", "HOTEL", "name", "  ")), // 空行跳过
                null, false, user);

        ProjectBudget budget = (ProjectBudget) saved.get("budget");
        assertThat(budget.getTotalAmount()).isEqualByComparingTo("4501.00"); // 3001.00+1500.00
        assertThat(budget.getStatus()).isEqualTo("DRAFT");
        @SuppressWarnings("unchecked")
        List<ProjectBudgetItem> items = (List<ProjectBudgetItem>) saved.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getAmount()).isEqualByComparingTo("3001.00");
    }

    @Test
    void saveRejectsWrongSectionForCategory() {
        when(projectService.requireProject(1L)).thenReturn(project("ONLINE"));
        when(budgetMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.save(1L,
                List.of(Map.of("section", "HOTEL", "name", "场地费")), null, false, user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("线上项目");
    }

    @Test
    void saveRejectsNegativePriceAndUnknownSection() {
        when(projectService.requireProject(1L)).thenReturn(project("OFFLINE"));
        when(budgetMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.save(1L,
                List.of(Map.of("section", "HOTEL", "name", "场地费", "unitPrice", -1)), null, false, user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("负数");
        assertThatThrownBy(() -> service.save(1L,
                List.of(Map.of("section", "OTHER", "name", "x")), null, false, user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分节");
    }

    @Test
    void confirmedBudgetRejectsDraftOverwrite() {
        when(projectService.requireProject(1L)).thenReturn(project("OFFLINE"));
        ProjectBudget confirmed = new ProjectBudget();
        confirmed.setId(10L);
        confirmed.setProjectId(1L);
        confirmed.setStatus("CONFIRMED");
        when(budgetMapper.selectOne(any())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.save(1L, List.of(), null, false, user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");
    }

    @Test
    void feeStandardSaveValidatesKeyFormatAndRangeValue() {
        assertThatThrownBy(() -> service.saveFeeStandards(
                List.of(Map.of("feeKey", "bad key", "name", "x")), user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fee_key");
        assertThatThrownBy(() -> service.saveFeeStandards(
                List.of(Map.of("feeKey", "OK_KEY", "name", "x", "defaultValue", "300-400")), user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("区间");
    }

    @Test
    void feeStandardSaveUpdatesExistingByKey() {
        ProjectBudgetFeeStandard existing = fee("MC_INTERLUDE", "500", "元/场");
        existing.setId(3L);
        when(feeStandardMapper.selectOne(any())).thenReturn(existing);
        when(feeStandardMapper.selectList(any())).thenReturn(List.of(existing));

        service.saveFeeStandards(List.of(Map.of("feeKey", "MC_INTERLUDE", "name", "串场",
                "defaultValue", "600", "unit", "元/场", "isActive", 1)), user);
        assertThat(existing.getDefaultValue()).isEqualTo("600");
    }
}
