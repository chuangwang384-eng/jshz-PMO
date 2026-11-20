package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectBudgetItem;
import com.jshz.expert.mapper.ProjectBudgetItemMapper;
import com.jshz.expert.mapper.ProjectBudgetMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议预算单导出（2026-11-20）：按历史结算单共性结构生成报价单/成本单 xlsx。
 * 共性结构 = 表头信息区 + 费用大类分节明细（单价×数量=小计活公式、分节小计）+ 汇总链
 * （费用合计 → 服务费6% → 不含税总价 → 增值税6% → 含税总价；汇总为导出时点静态数值）。
 * 报价单取报价单价/金额；成本单取成本单价/成本金额，并附报价含税合计与毛利参考。
 * 禁止在其他服务复制本导出结构。
 */
@Service
@RequiredArgsConstructor
public class ProjectBudgetExportService {

    private final ProjectBudgetMapper budgetMapper;
    private final ProjectBudgetItemMapper itemMapper;
    private final ProjectService projectService;

    /** 分节标签（与前端 ProjectBudgetPanel SECTIONS 一致） */
    private static final Map<String, String> SECTION_LABELS = new LinkedHashMap<>();

    static {
        SECTION_LABELS.put("HOTEL", "酒店费用");
        SECTION_LABELS.put("TRAFFIC", "交通费用");
        SECTION_LABELS.put("BUILD", "搭建费");
        SECTION_LABELS.put("MATERIAL", "物料费");
        SECTION_LABELS.put("MEDIA", "媒体费");
        SECTION_LABELS.put("GIFT", "礼品费");
        SECTION_LABELS.put("STAFF", "人员费");
        SECTION_LABELS.put("ONLINE_QUOTE", "报价明细");
    }

    public byte[] export(Long projectId, String docType, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireProjectReadable(project, user);
        ProjectBudget budget = budgetMapper.selectOne(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getProjectId, projectId));
        if (budget == null) {
            throw new BusinessException("尚未创建预算单，请先保存后再导出");
        }
        List<ProjectBudgetItem> items = itemMapper.selectList(new LambdaQueryWrapper<ProjectBudgetItem>()
                .eq(ProjectBudgetItem::getBudgetId, budget.getId())
                .orderByAsc(ProjectBudgetItem::getSort));
        boolean cost = "COST".equalsIgnoreCase(docType);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(cost ? "会议成本单" : "会议报价单");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            int r = headerBlock(sheet, bold, 0, project, cost);
            r = itemBlock(sheet, bold, r, items, cost);
            summaryBlock(sheet, bold, r, budget, items, cost);
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 30 * 256);
            for (int i = 2; i <= 6; i++) {
                sheet.setColumnWidth(i, 14 * 256);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("预算单导出失败: " + e.getMessage());
        }
    }

    /** 表头信息区（共性结构第 1 层）：标题 + 项目名称/时间/地点/负责人。 */
    private int headerBlock(Sheet sheet, CellStyle bold, int r, Project project, boolean cost) {
        Row title = sheet.createRow(r++);
        title.createCell(0).setCellValue(cost ? "景帅会展会议成本单" : "景帅会展会议报价单");
        title.getCell(0).setCellStyle(bold);
        putKV(sheet, r++, 0, "项目名称", project.getName());
        putKV(sheet, r++, 0, "会议时间", (project.getMeetingStartDate() == null ? "-" : project.getMeetingStartDate().toString())
                + " ~ " + (project.getMeetingEndDate() == null ? "-" : project.getMeetingEndDate().toString()));
        putKV(sheet, r++, 0, "客户名称", project.getClientCompany() == null ? "-" : project.getClientCompany());
        putKV(sheet, r++, 0, "客户负责人", project.getClientName() == null ? "-" : project.getClientName());
        putKV(sheet, r++, 0, "导出时间", java.time.LocalDateTime.now().toString().substring(0, 19).replace('T', ' '));
        return r + 1; // 空一行
    }

    private void putKV(Sheet sheet, int r, int c, String k, String v) {
        sheet.createRow(r).createCell(c).setCellValue(k + "：" + (v == null ? "-" : v));
    }

    /** 明细分节区（共性结构第 2 层）：分节行 + 明细行（小计=单价×数量 活公式）+ 分节小计。 */
    private int itemBlock(Sheet sheet, CellStyle bold, int r, List<ProjectBudgetItem> items, boolean cost) {
        String[] head = cost
                ? new String[]{"费用分节", "费用项", "成本单价", "单位", "数量", "成本金额", "备注"}
                : new String[]{"费用分节", "费用项", "单价", "单位", "数量", "小计", "备注"};
        Row hr = sheet.createRow(r++);
        for (int i = 0; i < head.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(head[i]);
            c.setCellStyle(bold);
        }
        for (Map.Entry<String, String> sec : SECTION_LABELS.entrySet()) {
            List<ProjectBudgetItem> rows = items.stream().filter(it -> sec.getKey().equals(it.getSection())).toList();
            if (rows.isEmpty()) {
                continue;
            }
            Row sr = sheet.createRow(r++);
            sr.createCell(0).setCellValue(sec.getValue());
            sr.getCell(0).setCellStyle(bold);
            int first = r;
            for (ProjectBudgetItem it : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(sec.getValue());
                row.createCell(1).setCellValue(it.getName() == null ? "" : it.getName());
                row.createCell(2).setCellValue((cost ? it.getCostPrice() : it.getUnitPrice()) == null
                        ? 0 : (cost ? it.getCostPrice() : it.getUnitPrice()).doubleValue());
                row.createCell(3).setCellValue(it.getUnit() == null ? "" : it.getUnit());
                row.createCell(4).setCellValue(it.getQuantity() == null ? 0 : it.getQuantity().doubleValue());
                row.createCell(5).setCellValue("=C" + r + "*E" + r);
                row.createCell(6).setCellValue(it.getRemark() == null ? "" : it.getRemark());
            }
            Row total = sheet.createRow(r++);
            total.createCell(1).setCellValue("费用小计：");
            total.getCell(1).setCellStyle(bold);
            total.createCell(5).setCellValue("=SUM(F" + first + ":F" + (r - 2) + ")");
            total.getCell(5).setCellStyle(bold);
        }
        return r + 1;
    }

    /** 汇总区（共性结构第 3 层，导出时点静态数值）。 */
    private void summaryBlock(Sheet sheet, CellStyle bold, int r, ProjectBudget budget,
                              List<ProjectBudgetItem> items, boolean cost) {
        BigDecimal quote = items.stream().map(ProjectBudgetItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal costTotal = items.stream().map(it -> it.getCostAmount() == null ? BigDecimal.ZERO : it.getCostAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceRate = budget.getServiceRate() == null ? new BigDecimal("0.06") : budget.getServiceRate();
        BigDecimal taxRate = budget.getTaxRate() == null ? new BigDecimal("0.06") : budget.getTaxRate();
        BigDecimal service = quote.multiply(serviceRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = quote.add(service);
        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxed = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);
        if (!cost) {
            putSum(sheet, r++, "费用合计：", quote, bold);
            putSum(sheet, r++, "服务费（" + rateTxt(serviceRate) + "）：", service, bold);
            putSum(sheet, r++, "不含税总价：", subtotal, bold);
            putSum(sheet, r++, "增值税（" + rateTxt(taxRate) + "）：", tax, bold);
            putSum(sheet, r++, "含税总价：", taxed, bold);
        } else {
            putSum(sheet, r++, "成本合计：", costTotal, bold);
            putSum(sheet, r++, "报价含税合计（参考）：", taxed, bold);
            putSum(sheet, r++, "毛利（参考）：", taxed.subtract(costTotal).setScale(2, RoundingMode.HALF_UP), bold);
        }
    }

    private String rateTxt(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%";
    }

    private void putSum(Sheet sheet, int r, String label, BigDecimal value, CellStyle bold) {
        Row row = sheet.createRow(r);
        Cell labelCell = row.createCell(1);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(bold);
        Cell v = row.createCell(5);
        v.setCellValue(value.doubleValue());
        v.setCellStyle(bold);
    }
}
