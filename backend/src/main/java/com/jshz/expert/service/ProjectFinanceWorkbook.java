package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

final class ProjectFinanceWorkbook {
    private record Layout(String sheet, String columns, List<String> required) {}

    private static final Map<String, Layout> LAYOUTS = Map.of(
            "PLATFORM", new Layout("平台类项目",
                    "subType platform ourCompany ourOwner dept meetingDate clientContact location meetingName sponsorAmount sponsorCompany productLine province remark",
                    List.of("subType", "ourCompany")),
            "DIRECT_PAY", new Layout("直付类项目",
                    "subType ourCompany expenseType ourOwner dept meetingDate clientContact location meetingName companyName businessUnit productLine province bookDept sfaNo remark",
                    List.of("subType", "ourCompany", "companyName", "province")),
            "KEY", new Layout("重点类项目",
                    "meetingDate clientContact location meetingName clientOwner clientWorkUnit remark", List.of()));

    private ProjectFinanceWorkbook() {}

    static byte[] generate(String projectType, Map<String, Object> values) {
        Layout layout = projectType == null ? null : LAYOUTS.get(projectType);
        if (layout == null) throw new BusinessException("请先选择项目类型");
        for (String required : List.of("meetingName", "meetingDate")) requireText(values, required);
        for (String required : layout.required()) requireText(values, required);
        try (var template = ProjectFinanceWorkbook.class.getResourceAsStream("/templates/project-finance.xlsx");
             var workbook = new XSSFWorkbook(template);
             var result = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheet(layout.sheet());
            for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
                String name = workbook.getSheetName(i);
                if (!name.equals(layout.sheet()) && !name.equals("Sheet4")) workbook.removeSheetAt(i);
            }
            workbook.setActiveSheet(workbook.getSheetIndex(sheet));
            var row = sheet.getRow(2);
            if (row == null) row = sheet.createRow(2);
            row.getCell(0, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(1);
            row.getCell(1, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(layout.sheet());
            String[] columns = layout.columns().split(" ");
            for (int i = 0; i < columns.length; i++) {
                Object value = values.get(columns[i]);
                if (value == null) continue;
                var cell = row.getCell(i + 2, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if ("sponsorAmount".equals(columns[i]) && value instanceof Number amount
                        && Double.isFinite(amount.doubleValue()) && amount.doubleValue() >= 0) {
                    cell.setCellValue(amount.doubleValue());
                } else if (value instanceof String text && text.length() <= 500) {
                    cell.setCellValue(text);
                } else {
                    throw new BusinessException("财务表单字段格式错误：" + columns[i]);
                }
            }
            workbook.write(result);
            return result.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成合规立项申请表失败");
        }
    }

    private static void requireText(Map<String, Object> values, String field) {
        if (!(values.get(field) instanceof String text) || text.isBlank()) {
            throw new BusinessException("请填写财务表单必填项：" + field);
        }
    }
}
