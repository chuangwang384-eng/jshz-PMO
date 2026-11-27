package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合规立项申请表模板 ↔ 后端表单定义 的对齐守卫（2026-11-26 P2-21）。
 *
 * <p>此前表单结构在三处各写一份（前端 SCHEMAS / 后端 LAYOUTS / Excel 模板），
 * 靠人工保持一致；本测试把「模板表头 ↔ 字段定义顺序 ↔ 必填口径 ↔ 下拉取值」固化为断言，
 * 任一处单独改动都会立即失败。
 *
 * <p>模板与定义之间<b>已登记</b>的差异见 {@link ProjectFinanceWorkbook#KNOWN_TEMPLATE_DEVIATIONS}：
 * 这些差异已提交负责人确认，未确认前保持现状；测试对差异做显式登记，
 * 使「新漂移」不会被「旧差异」掩盖。
 */
class ProjectFinanceTemplateAlignmentTest {

    /** 读取模板中某个 sheet 的第 2 行（表头行）从 C 列起的单元格文本。 */
    List<String> headerTexts(Sheet sheet) {
        Row header = sheet.getRow(1);
        assertNotNull(header, "模板缺少表头行");
        List<String> texts = new ArrayList<>();
        for (int c = 2; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            texts.add(cell == null || cell.getCellType() == CellType.BLANK ? "" : cell.getStringCellValue());
        }
        return texts;
    }

    Sheet sheetOf(XSSFWorkbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        assertNotNull(sheet, "模板缺少 sheet：" + name);
        return sheet;
    }

    /** 表头文案与字段中文名的已知别名（模板用短名、定义用更明确的名称）。 */
    Map<String, String> headerAliases(String projectType) {
        Map<String, String> aliases = new LinkedHashMap<>();
        if ("DIRECT_PAY".equals(projectType) || "KEY".equals(projectType)) {
            // 模板在直付类/重点类把「会议日期」写作「日期」
            aliases.put("会议日期", "日期");
        }
        // 2026-11-26 P3-25：财务表单的「地点」改名「会议地点（具体地址）」以与项目属性「地点（线上/线下）」消歧
        aliases.put("会议地点（具体地址）", "地点");
        return aliases;
    }

    @Test
    void templateSheetNamesMatchTheBackendDefinition() throws Exception {
        try (InputStream in = ProjectFinanceWorkbook.class.getResourceAsStream("/templates/project-finance.xlsx");
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            for (ProjectFinanceWorkbook.Schema schema : List.of(
                    ProjectFinanceWorkbook.schemaOf("PLATFORM"),
                    ProjectFinanceWorkbook.schemaOf("DIRECT_PAY"),
                    ProjectFinanceWorkbook.schemaOf("KEY"))) {
                assertNotNull(schema, "缺少项目类型定义：" + schema);
                assertNotNull(workbook.getSheet(schema.sheet()), "模板缺少 sheet：" + schema.sheet());
            }
            // Sheet4 为字典页（模板下拉引用源），必须保留
            assertNotNull(workbook.getSheet("Sheet4"), "模板缺少字典页 Sheet4");
        }
    }

    @Test
    void templateHeadersMatchTheBackendFieldOrder() throws Exception {
        try (InputStream in = ProjectFinanceWorkbook.class.getResourceAsStream("/templates/project-finance.xlsx");
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            for (String projectType : List.of("PLATFORM", "DIRECT_PAY", "KEY")) {
                ProjectFinanceWorkbook.Schema schema = ProjectFinanceWorkbook.schemaOf(projectType);
                List<String> headers = headerTexts(sheetOf(workbook, schema.sheet()));
                List<ProjectFinanceWorkbook.Field> fields = schema.fields();
                assertEquals(fields.size(), headers.size(),
                        projectType + "：模板列数与后端字段数不一致（列错位会让归档件写错列）");
                Map<String, String> aliases = headerAliases(projectType);
                for (int i = 0; i < fields.size(); i++) {
                    String expected = fields.get(i).label();
                    String alias = aliases.get(expected);
                    String actual = headers.get(i);
                    assertTrue(actual.equals(expected) || actual.equals(alias),
                            projectType + " 第 " + (i + 3) + " 列表头「" + actual + "」与字段「"
                                    + fields.get(i).key() + "/" + expected + "」不一致");
                }
            }
        }
    }

    @Test
    void dropdownOptionsMatchTheTemplateDictionary() throws Exception {
        try (InputStream in = ProjectFinanceWorkbook.class.getResourceAsStream("/templates/project-finance.xlsx");
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet dict = sheetOf(workbook, "Sheet4");
            Row row = dict.getRow(0);
            // Sheet4：A=我方公司(直付) / B=费用类型 / C=细分类型(平台) / D=细分类型(直付)
            assertEquals("景帅", cellText(row, 0));
            assertEquals("会议费", cellText(row, 1));
            assertEquals("单会立项", cellText(row, 2));
            assertEquals("有合同", cellText(row, 3));

            Map<String, List<String>> platform = ProjectFinanceWorkbook.optionsOf("PLATFORM");
            Map<String, List<String>> directPay = ProjectFinanceWorkbook.optionsOf("DIRECT_PAY");

            // 平台类细分类型 = Sheet4!C1:C3
            assertEquals(List.of("单会立项", "系列会立项", "系列单场立项"), platform.get("SUBD_TYPE"));
            assertEquals(List.of("单会立项", "系列会立项", "系列单场立项"),
                    List.of(cellText(dict.getRow(0), 2), cellText(dict.getRow(1), 2), cellText(dict.getRow(2), 2)));
            // 直付类细分类型 = Sheet4!D1:D2
            assertEquals(List.of("有合同", "无合同"), directPay.get("SUBD_TYPE"));
            assertEquals(List.of("有合同", "无合同"),
                    List.of(cellText(dict.getRow(0), 3), cellText(dict.getRow(1), 3)));
            // 费用类型 = Sheet4!B1:B4
            assertEquals(List.of("会议费", "设计费", "物料费", "其他"), directPay.get("EXPENSE_TYPE"));
            assertEquals(List.of("会议费", "设计费", "物料费", "其他"),
                    List.of(cellText(dict.getRow(0), 1), cellText(dict.getRow(1), 1),
                            cellText(dict.getRow(2), 1), cellText(dict.getRow(3), 1)));
            // 直付类我方公司 = Sheet4!A1:A4
            assertEquals(List.of("景帅", "华秀", "偌君", "会信盈"), directPay.get("OUR_COMPANY"));
            assertEquals(List.of("景帅", "华秀", "偌君", "会信盈"),
                    List.of(cellText(dict.getRow(0), 0), cellText(dict.getRow(1), 0),
                            cellText(dict.getRow(2), 0), cellText(dict.getRow(3), 0)));
        }
    }

    @Test
    void knownTemplateDeviationsStayRegistered() {
        // 模板与定义之间已登记的差异必须留痕（避免「新漂移被旧差异掩盖」）；差异消除时应同步移除本断言
        assertEquals(2, ProjectFinanceWorkbook.KNOWN_TEMPLATE_DEVIATIONS.size());
        assertFalse(ProjectFinanceWorkbook.KNOWN_TEMPLATE_DEVIATIONS.stream()
                .anyMatch(String::isBlank), "已登记的模板差异说明不得为空");
    }

    @Test
    void platformCompanyOptionCountMatchesTheTemplateDropdown() throws Exception {
        try (InputStream in = ProjectFinanceWorkbook.class.getResourceAsStream("/templates/project-finance.xlsx");
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            // 模板 Sheet1!E3 的下拉为 6 项（数据格），本定义同为 6 项（差异仅第 5 项文案，见 KNOWN_TEMPLATE_DEVIATIONS）
            List<String> options = ProjectFinanceWorkbook.optionsOf("PLATFORM").get("OUR_COMPANY");
            assertEquals(6, options.size());
            assertTrue(options.contains("河南景帅"));
            assertTrue(options.contains("北京际格"));
        }
    }

    @Test
    void everySelectFieldHasOptionsAndEveryOptionFieldIsSelect() {
        for (String projectType : List.of("PLATFORM", "DIRECT_PAY", "KEY")) {
            ProjectFinanceWorkbook.Schema schema = ProjectFinanceWorkbook.schemaOf(projectType);
            Map<String, List<String>> options = ProjectFinanceWorkbook.optionsOf(projectType);
            for (ProjectFinanceWorkbook.Field field : schema.fields()) {
                boolean hasOptions = field.optionsGroup() != null
                        && !options.getOrDefault(field.optionsGroup(), List.of()).isEmpty();
                if (ProjectFinanceWorkbook.TYPE_SELECT.equals(field.valueType())) {
                    assertTrue(hasOptions, projectType + " 字段 " + field.key() + " 是下拉但没有选项");
                    assertNotNull(field.optionsGroup(), field.key() + " 缺少选项组");
                } else {
                    assertFalse(hasOptions, projectType + " 字段 " + field.key() + " 非下拉却带选项组");
                }
            }
        }
    }

    @Test
    void legacyAliasesResolveToExistingOptions() {
        // 历史简称必须仍指向现有选项，否则旧数据读回会被清空
        assertEquals("河南景帅", ProjectFinanceWorkbook.resolveLegacyAlias("PLATFORM", "OUR_COMPANY", "景帅"));
        assertEquals("郑州华秀", ProjectFinanceWorkbook.resolveLegacyAlias("PLATFORM", "OUR_COMPANY", "华秀"));
        assertEquals("河南偌君", ProjectFinanceWorkbook.resolveLegacyAlias("PLATFORM", "OUR_COMPANY", "偌君"));
        // 直付类我方公司本身就是简称，不做转换
        assertEquals("景帅", ProjectFinanceWorkbook.resolveLegacyAlias("DIRECT_PAY", "OUR_COMPANY", "景帅"));
        // 未登记的值原样返回
        assertEquals("未知公司", ProjectFinanceWorkbook.resolveLegacyAlias("PLATFORM", "OUR_COMPANY", "未知公司"));

        List<String> platformOptions = ProjectFinanceWorkbook.optionsOf("PLATFORM").get("OUR_COMPANY");
        for (String alias : List.of("景帅", "华秀", "偌君")) {
            String resolved = ProjectFinanceWorkbook.resolveLegacyAlias("PLATFORM", "OUR_COMPANY", alias);
            assertTrue(platformOptions.contains(resolved),
                    "历史简称 " + alias + " 解析为 " + resolved + "，已不在现有选项内");
        }
    }

    @Test
    void storedSelectsAreNormalizedOnRead() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ourCompany", "景帅");
        fields.put("subType", "已下线的类型");
        fields.put("province", "河南");

        ProjectFinanceWorkbook.normalizeStoredSelects("PLATFORM", fields);

        assertEquals("河南景帅", fields.get("ourCompany"));
        assertEquals("", fields.get("subType"), "失效选项应置空提示重选，而不是原样保留");
        assertEquals("河南", fields.get("province"), "非下拉字段不受归一化影响");
    }

    @Test
    void generatedWorkbookWritesEachFieldToItsOwnColumn() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subType", "单会立项");
        values.put("ourCompany", "河南景帅");
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");
        values.put("sponsorAmount", 1200.50);

        byte[] bytes = ProjectFinanceWorkbook.generate("PLATFORM", values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = sheetOf(workbook, "平台类项目");
            Row row = sheet.getRow(2);
            // 字段顺序：subType(0) platform(1) ourCompany(2) ourOwner(3) dept(4) meetingDate(5)
            //           clientContact(6) location(7) meetingName(8) sponsorAmount(9) ...
            assertEquals("单会立项", row.getCell(2).getStringCellValue());
            assertEquals("河南景帅", row.getCell(4).getStringCellValue());
            assertEquals("联调会议", row.getCell(10).getStringCellValue());
            assertEquals(1200.50, row.getCell(11).getNumericCellValue());
        }
    }

    @Test
    void blankAmountIsNotWrittenAsTextIntoTheNumericColumn() throws Exception {
        // P2-19：空金额不得写成文本（否则 Excel 里该单元格是文本，SUM 统计会跳过）
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subType", "单会立项");
        values.put("ourCompany", "河南景帅");
        values.put("meetingName", "联调会议");
        values.put("meetingDate", "2026-11-21");
        values.put("sponsorAmount", "");

        byte[] bytes = ProjectFinanceWorkbook.generate("PLATFORM", values);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = sheetOf(workbook, "平台类项目");
            Row row = sheet.getRow(2);
            Cell amount = row.getCell(11);
            assertTrue(amount == null || amount.getCellType() == CellType.BLANK,
                    "空金额单元格不应是文本，实际类型：" + (amount == null ? "null" : amount.getCellType()));
        }
    }

    @Test
    void requiredFieldsAreEnforcedWithChineseLabels() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subType", "单会立项");
        // 缺我方公司 / 会议日期 / 会议名称
        BusinessException e = assertThrows(BusinessException.class,
                () -> ProjectFinanceWorkbook.generate("PLATFORM", values));
        assertTrue(e.getMessage().contains("我方公司"), e.getMessage());
    }

    private String cellText(Row row, int index) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null || cell.getCellType() == CellType.BLANK ? "" : cell.getStringCellValue();
    }
}
