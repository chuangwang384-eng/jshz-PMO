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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议预算单导出（2026-11-20；2026-11-21 美化+公式修复）：按历史结算单共性结构生成报价单/成本单 xlsx。
 * 共性结构 = 表头信息区 + 费用大类分节明细 + 汇总链（费用合计 → 服务费6% → 不含税总价 → 增值税6% → 含税总价）。
 * 公式修复：行小计/分节小计必须走 setCellFormula（setCellValue("=..") 会存成文本，Excel 不计算——1.0.88 事故根因）；
 * 汇总链为导出时点静态数值并标注"导出快照"。成本单取成本单价/成本金额，附报价含税合计与毛利参考。
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

    private static final String MONEY_FMT = "#,##0.00";
    private static final String QTY_FMT = "#,##0.##";

    private CellStyle titleStyle;
    private CellStyle headStyle;
    private CellStyle sectionStyle;
    private CellStyle textStyle;
    private CellStyle moneyStyle;
    private CellStyle qtyStyle;
    private CellStyle subtotalStyle;
    private CellStyle subtotalMoneyStyle;
    private CellStyle summaryLabelStyle;
    private CellStyle summaryMoneyStyle;
    private CellStyle grandMoneyStyle;

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
            // POI 公式无缓存结果，强制打开时全量重算；setForceFormulaRecalculation 在 POI 5.4
            // 不落盘（实测 calcId 未变），故直接写 CTWorkbook 的 calcPr 标记（1.0.90 二次修复）
            var calcPr = wb.getCTWorkbook().getCalcPr();
            if (calcPr == null) {
                calcPr = wb.getCTWorkbook().addNewCalcPr();
            }
            calcPr.setCalcId(0);
            calcPr.setFullCalculationOnLoad(true);
            initStyles(wb);
            Sheet sheet = wb.createSheet(cost ? "会议成本单" : "会议报价单");
            sheet.setDisplayGridlines(false);
            int r = headerBlock(wb, sheet, 0, project, cost);
            r = itemBlock(sheet, r, items, cost);
            r = summaryBlock(sheet, r, budget, items, cost);
            // 列宽：A 分节 / B 费用项 / C 单价 / D 单位 / E 数量 / F 金额 / G 备注
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 32 * 256);
            sheet.setColumnWidth(2, 13 * 256);
            sheet.setColumnWidth(3, 11 * 256);
            sheet.setColumnWidth(4, 10 * 256);
            sheet.setColumnWidth(5, 15 * 256);
            sheet.setColumnWidth(6, 34 * 256);
            // 表头行冻结（表头在数据区第 1 行，含标题块偏移后冻结全部表头以上内容）
            sheet.createFreezePane(0, 8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("预算单导出失败: " + e.getMessage());
        }
    }

    private void initStyles(XSSFWorkbook wb) {
        titleStyle = wb.createCellStyle();
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);

        headStyle = wb.createCellStyle();
        Font headFont = wb.createFont();
        headFont.setBold(true);
        headFont.setColor(IndexedColors.WHITE.getIndex());
        headStyle.setFont(headFont);
        headStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(headStyle);
        headStyle.setAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        sectionStyle = wb.createCellStyle();
        Font sectionFont = wb.createFont();
        sectionFont.setBold(true);
        sectionStyle.setFont(sectionFont);
        sectionStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(sectionStyle);

        textStyle = wb.createCellStyle();
        border(textStyle);
        textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        textStyle.setWrapText(true);

        moneyStyle = wb.createCellStyle();
        border(moneyStyle);
        moneyStyle.setDataFormat(wb.createDataFormat().getFormat(MONEY_FMT));

        qtyStyle = wb.createCellStyle();
        border(qtyStyle);
        qtyStyle.setDataFormat(wb.createDataFormat().getFormat(QTY_FMT));

        subtotalStyle = wb.createCellStyle();
        Font subFont = wb.createFont();
        subFont.setBold(true);
        subtotalStyle.setFont(subFont);
        border(subtotalStyle);
        subtotalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        subtotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        subtotalMoneyStyle = wb.createCellStyle();
        Font subMoneyFont = wb.createFont();
        subMoneyFont.setBold(true);
        subtotalMoneyStyle.setFont(subMoneyFont);
        subtotalMoneyStyle.cloneStyleFrom(moneyStyle);
        subtotalMoneyStyle.setFont(subMoneyFont);
        subtotalMoneyStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        subtotalMoneyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        summaryLabelStyle = wb.createCellStyle();
        Font summaryFont = wb.createFont();
        summaryFont.setBold(true);
        summaryFont.setFontHeightInPoints((short) 12);
        summaryLabelStyle.setFont(summaryFont);

        summaryMoneyStyle = wb.createCellStyle();
        Font summaryMoneyFont = wb.createFont();
        summaryMoneyFont.setBold(true);
        summaryMoneyFont.setFontHeightInPoints((short) 12);
        summaryMoneyStyle.setFont(summaryMoneyFont);
        summaryMoneyStyle.setDataFormat(wb.createDataFormat().getFormat(MONEY_FMT));

        grandMoneyStyle = wb.createCellStyle();
        Font grandFont = wb.createFont();
        grandFont.setBold(true);
        grandFont.setFontHeightInPoints((short) 14);
        grandFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        grandMoneyStyle.setFont(grandFont);
        grandMoneyStyle.setDataFormat(wb.createDataFormat().getFormat(MONEY_FMT));
    }

    private void border(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /** 表头信息区（共性结构第 1 层）：合并单元格标题 + 项目信息。 */
    private int headerBlock(XSSFWorkbook wb, Sheet sheet, int r, Project project, boolean cost) {
        Row title = sheet.createRow(r++);
        Cell t = title.createCell(0);
        t.setCellValue(cost ? "景帅会展会议成本单" : "景帅会展会议报价单");
        t.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 6));
        title.setHeightInPoints(28);

        String time = (project.getMeetingStartDate() == null ? "-" : project.getMeetingStartDate().toString())
                + " ~ " + (project.getMeetingEndDate() == null ? "-" : project.getMeetingEndDate().toString());
        String[][] kvs = {
                {"项目名称", project.getName() == null ? "-" : project.getName()},
                {"会议时间", time},
                {"客户名称", project.getClientCompany() == null ? "-" : project.getClientCompany()},
                {"客户负责人", project.getClientName() == null ? "-" : project.getClientName()},
                {"导出时间", java.time.LocalDateTime.now().toString().substring(0, 19).replace('T', ' ')}
        };
        for (String[] kv : kvs) {
            Row row = sheet.createRow(r++);
            Cell k = row.createCell(0);
            k.setCellValue(kv[0] + "：");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle ks = wb.createCellStyle();
            ks.setFont(bold);
            k.setCellStyle(ks);
            Cell v = row.createCell(1);
            v.setCellValue(kv[1]);
        }
        return r + 1; // 空一行
    }

    /** 明细分节区（共性结构第 2 层）：表头 + 分节行 + 明细行（小计=单价×数量 活公式）+ 分节小计。 */
    private int itemBlock(Sheet sheet, int r, List<ProjectBudgetItem> items, boolean cost) {
        String[] head = cost
                ? new String[]{"费用分节", "费用项", "成本单价", "单位", "数量", "成本金额", "备注"}
                : new String[]{"费用分节", "费用项", "单价", "单位", "数量", "小计", "备注"};
        Row hr = sheet.createRow(r++);
        hr.setHeightInPoints(22);
        for (int i = 0; i < head.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(head[i]);
            c.setCellStyle(headStyle);
        }
        for (Map.Entry<String, String> sec : SECTION_LABELS.entrySet()) {
            List<ProjectBudgetItem> rows = items.stream().filter(it -> sec.getKey().equals(it.getSection())).toList();
            if (rows.isEmpty()) {
                continue;
            }
            Row sr = sheet.createRow(r++);
            sr.setHeightInPoints(20);
            Cell sc = sr.createCell(0);
            sc.setCellValue(sec.getValue());
            for (int i = 0; i < head.length; i++) {
                sr.createCell(i).setCellStyle(sectionStyle);
            }
            sc.setCellStyle(sectionStyle);
            int first = r;
            for (ProjectBudgetItem it : rows) {
                Row row = sheet.createRow(r++);
                Cell a = row.createCell(0);
                a.setCellValue(sec.getValue());
                a.setCellStyle(textStyle);
                Cell b = row.createCell(1);
                b.setCellValue(it.getName() == null ? "" : it.getName());
                b.setCellStyle(textStyle);
                Cell price = row.createCell(2);
                BigDecimal p = cost ? it.getCostPrice() : it.getUnitPrice();
                price.setCellValue(p == null ? 0 : p.doubleValue());
                price.setCellStyle(moneyStyle);
                Cell unit = row.createCell(3);
                unit.setCellValue(it.getUnit() == null ? "" : it.getUnit());
                unit.setCellStyle(textStyle);
                Cell qty = row.createCell(4);
                qty.setCellValue(it.getQuantity() == null ? 0 : it.getQuantity().doubleValue());
                qty.setCellStyle(qtyStyle);
                // 公式必须 setCellFormula；setCellValue("=..") 是文本不计算（1.0.88 事故根因）
                row.createCell(5).setCellFormula("C" + r + "*E" + r);
                row.getCell(5).setCellStyle(moneyStyle);
                Cell remark = row.createCell(6);
                remark.setCellValue(it.getRemark() == null ? "" : it.getRemark());
                remark.setCellStyle(textStyle);
            }
            Row total = sheet.createRow(r++);
            Cell label = total.createCell(1);
            label.setCellValue(sec.getValue() + " · 费用小计");
            label.setCellStyle(subtotalStyle);
            for (int i = 2; i <= 4; i++) {
                total.createCell(i).setCellStyle(subtotalStyle);
            }
            Cell sum = total.createCell(5);
            sum.setCellFormula("SUM(F" + first + ":F" + (r - 2) + ")");
            sum.setCellStyle(subtotalMoneyStyle);
            total.createCell(6).setCellStyle(subtotalStyle);
        }
        return r + 1;
    }

    /** 汇总区（共性结构第 3 层，导出时点静态数值，注明快照）。 */
    private int summaryBlock(Sheet sheet, int r, ProjectBudget budget,
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
            r = putSum(sheet, r, "费用合计：", quote, summaryLabelStyle, summaryMoneyStyle);
            r = putSum(sheet, r, "服务费（" + rateTxt(serviceRate) + "）：", service, summaryLabelStyle, summaryMoneyStyle);
            r = putSum(sheet, r, "不含税总价：", subtotal, summaryLabelStyle, summaryMoneyStyle);
            r = putSum(sheet, r, "增值税（" + rateTxt(taxRate) + "）：", tax, summaryLabelStyle, summaryMoneyStyle);
            r = putSum(sheet, r, "含税总价：", taxed, summaryLabelStyle, grandMoneyStyle);
            Row note = sheet.createRow(r++);
            note.createCell(1).setCellValue("注：金额为导出时点快照，服务费/增值税率 6%（历史结算单口径）。");
        } else {
            r = putSum(sheet, r, "成本合计：", costTotal, summaryLabelStyle, summaryMoneyStyle);
            r = putSum(sheet, r, "报价含税合计（参考）：", taxed, summaryLabelStyle, summaryMoneyStyle);
            r = putSum(sheet, r, "毛利（参考）：", taxed.subtract(costTotal).setScale(2, RoundingMode.HALF_UP), summaryLabelStyle, grandMoneyStyle);
            Row note = sheet.createRow(r++);
            note.createCell(1).setCellValue("注：报价含税合计/毛利为导出时点快照（费率 6%）。");
        }
        return r;
    }

    private String rateTxt(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%";
    }

    private int putSum(Sheet sheet, int r, String label, BigDecimal value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(r);
        row.setHeightInPoints(20);
        Cell labelCell = row.createCell(1);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        Cell v = row.createCell(5);
        v.setCellValue(value.doubleValue());
        v.setCellStyle(valueStyle);
        return r + 1;
    }
}
