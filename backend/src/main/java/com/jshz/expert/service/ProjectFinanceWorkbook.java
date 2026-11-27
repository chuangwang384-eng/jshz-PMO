package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会前财务资料（合规立项申请表）的<b>唯一真源</b>（2026-11-26 P2-13 / P2-21 收口）。
 *
 * <p>此前同一份表单结构散落三处：前端 SCHEMAS、后端 LAYOUTS、Excel 模板，三者靠人工保持一致，
 * 任何一处单独改动都会造成「归档件列错位 / 下拉选不到 / 必填判定不一致」。
 *
 * <p>现在本类同时持有：分区与字段定义（顺序即 Excel 列序）、字段中文名、必填口径、下拉选项与旧值别名；
 * Excel 列清单由字段定义直接派生（不再手写字符串），前端经
 * {@code GET /api/project/finance-schema} 拉取同一份定义，从结构上消除副本。
 *
 * <p>字段顺序与 Excel 模板的一致性由 {@link ProjectFinanceTemplateAlignmentTest} 断言守护；
 * 模板与本定义之间已知的文案/取值差异由该测试显式登记（见 {@link #KNOWN_TEMPLATE_DEVIATIONS}），
 * 差异范围扩大即测试失败，避免「新漂移被旧差异掩盖」。
 */
final class ProjectFinanceWorkbook {

    /** 字段值类型（决定前端控件与后端取值口径）。 */
    static final String TYPE_TEXT = "TEXT";
    static final String TYPE_TEXTAREA = "TEXTAREA";
    static final String TYPE_SELECT = "SELECT";
    static final String TYPE_NUMBER = "NUMBER";
    /** 取自项目主数据（会议日期 / 会议名称 / 我方负责人），前端只读、不可填写。 */
    static final String TYPE_PROJECT_FIELD = "PROJECT_FIELD";

    /**
     * 字段定义。
     *
     * @param key                   form_data JSON 键（与 Excel 列一一对应）
     * @param label                 中文字段名（与模板表头一致；同时用于错误提示，前端展示同一份）
     * @param valueType             值类型
     * @param required              是否必填
     * @param requiredWhenCompanyIs 条件必填：公司名称含该值时必填（申请表「恒瑞需填写所有信息」口径）
     * @param optionsGroup          下拉选项组（仅 TYPE_SELECT）
     * @param unit                  单位后缀（仅展示，如「（元）」；不参与模板表头比对）
     * @param excelDate             归档时是否按 Excel 原生日期写入（可排序/筛选/计算，见 P3-22）
     */
    record Field(String key, String label, String valueType, boolean required,
                 String requiredWhenCompanyIs, String optionsGroup, String unit, boolean excelDate) {
        /** 实际必填判定（公司名称含条件值时条件字段也必填）。 */
        boolean isRequired(String companyName) {
            if (required) {
                return true;
            }
            return requiredWhenCompanyIs != null && companyName != null
                    && companyName.contains(requiredWhenCompanyIs);
        }
    }

    /** 分区（对应 Excel 表头的合并区域）。 */
    record Section(String key, String title, String note, List<Field> fields) {}

    /** 项目类型的表单定义。 */
    record Schema(String projectType, String sheet, List<Section> sections) {
        /** 扁平字段顺序＝Excel 列顺序（自 C 列起）。 */
        List<Field> fields() {
            List<Field> all = new ArrayList<>();
            sections.forEach(section -> all.addAll(section.fields()));
            return List.copyOf(all);
        }
    }

    // ==================== 分区与字段定义（唯一真源） ====================

    private static final Field SUB_TYPE = select("subType", "细分类型", "SUBD_TYPE", true);
    private static final Field PLATFORM_NAME = text("platform", "依托平台");
    private static final Field OUR_COMPANY = select("ourCompany", "我方公司", "OUR_COMPANY", true);
    private static final Field EXPENSE_TYPE = select("expenseType", "费用类型", "EXPENSE_TYPE", false);
    private static final Field OUR_OWNER = projectField("ourOwner", "我方项目负责人", false, false);
    private static final Field DEPT = text("dept", "会议归属部门");
    /** 会议日期：取自项目主数据，归档按 Excel 原生日期写入（P3-22）。 */
    private static final Field MEETING_DATE = projectField("meetingDate", "会议日期", true, true);
    private static final Field CLIENT_CONTACT = text("clientContact", "客户对接人");
    /** 2026-11-26 P3-25：与项目属性「地点（线上/线下）」消歧，改名「会议地点（具体地址）」。 */
    private static final Field LOCATION = text("location", "会议地点（具体地址）");
    private static final Field MEETING_NAME = projectField("meetingName", "会议/项目名称", true, false);
    private static final Field SPONSOR_AMOUNT = number("sponsorAmount", "赞助金额", "（元）");
    private static final Field SPONSOR_COMPANY = text("sponsorCompany", "赞助企业");
    private static final Field PRODUCT_LINE = text("productLine", "产品线");
    private static final Field REMARK = textarea("remark", "备注");
    private static final Field CLIENT_OWNER = text("clientOwner", "客户负责人");
    private static final Field CLIENT_WORK_UNIT = text("clientWorkUnit", "客户工作单位");

    /** 平台类「企业信息」分区：省区选填。 */
    private static final Field PROVINCE_OPTIONAL = text("province", "省区");
    /** 直付类「提供内容」分区：省区必填（非恒瑞公司也须填）。 */
    private static final Field PROVINCE_REQUIRED = requiredText("province", "省区");
    /** 直付类「提供内容」分区：公司名称必填；恒瑞时本分区其余字段一并必填。 */
    private static final Field COMPANY_NAME = requiredText("companyName", "公司名称");
    private static final Field BUSINESS_UNIT = conditionalText("businessUnit", "事业部", "恒瑞");
    private static final Field PRODUCT_LINE_DIRECT = conditionalText("productLine", "产品线", "恒瑞");
    private static final Field BOOK_DEPT = conditionalText("bookDept", "记账部门", "恒瑞");
    private static final Field SFA_NO = conditionalText("sfaNo", "SFA/HOPES/CRM/钉钉", "恒瑞");

    private static Field text(String key, String label) {
        return new Field(key, label, TYPE_TEXT, false, null, null, null, false);
    }

    private static Field requiredText(String key, String label) {
        return new Field(key, label, TYPE_TEXT, true, null, null, null, false);
    }

    private static Field conditionalText(String key, String label, String whenCompanyIs) {
        return new Field(key, label, TYPE_TEXT, false, whenCompanyIs, null, null, false);
    }

    private static Field textarea(String key, String label) {
        return new Field(key, label, TYPE_TEXTAREA, false, null, null, null, false);
    }

    private static Field select(String key, String label, String optionsGroup, boolean required) {
        return new Field(key, label, TYPE_SELECT, required, null, optionsGroup, null, false);
    }

    private static Field number(String key, String label, String unit) {
        return new Field(key, label, TYPE_NUMBER, false, null, null, unit, false);
    }

    private static Field projectField(String key, String label, boolean required, boolean excelDate) {
        return new Field(key, label, TYPE_PROJECT_FIELD, required, null, null, null, excelDate);
    }

    private static final String NOTE_HENGRUI = "恒瑞需填写所有信息，其他公司仅需公司名称及省区";

    private static final Map<String, Schema> SCHEMAS = buildSchemas();

    private static Map<String, Schema> buildSchemas() {
        Map<String, Schema> schemas = new LinkedHashMap<>();
        schemas.put("PLATFORM", new Schema("PLATFORM", "平台类项目", List.of(
                new Section("type", "项目类型", null, List.of(SUB_TYPE)),
                new Section("meeting", "会议信息", null, List.of(
                        PLATFORM_NAME, OUR_COMPANY, OUR_OWNER, DEPT, MEETING_DATE, CLIENT_CONTACT, LOCATION, MEETING_NAME)),
                new Section("biz", "企业信息", null, List.of(
                        SPONSOR_AMOUNT, SPONSOR_COMPANY, PRODUCT_LINE, PROVINCE_OPTIONAL)),
                new Section("remark", "备注", null, List.of(REMARK)))));
        schemas.put("DIRECT_PAY", new Schema("DIRECT_PAY", "直付类项目", List.of(
                new Section("type", "项目类型", null, List.of(SUB_TYPE)),
                new Section("meeting", "会议信息", null, List.of(
                        OUR_COMPANY, EXPENSE_TYPE, OUR_OWNER, DEPT, MEETING_DATE, CLIENT_CONTACT, LOCATION, MEETING_NAME)),
                new Section("biz", "提供内容", NOTE_HENGRUI, List.of(
                        COMPANY_NAME, BUSINESS_UNIT, PRODUCT_LINE_DIRECT, PROVINCE_REQUIRED, BOOK_DEPT, SFA_NO)),
                new Section("remark", "备注", null, List.of(REMARK)))));
        schemas.put("KEY", new Schema("KEY", "重点类项目", List.of(
                new Section("meeting", "会议信息", null, List.of(
                        MEETING_DATE, CLIENT_CONTACT, LOCATION, MEETING_NAME)),
                new Section("biz", "提供内容", null, List.of(CLIENT_OWNER, CLIENT_WORK_UNIT)),
                new Section("remark", "备注", null, List.of(REMARK)))));
        return Map.copyOf(schemas);
    }

    // ==================== 下拉选项 ====================

    /**
     * 下拉选项组（后端唯一真源，前端经接口拉取）。
     * 细分类型取模板 Sheet4!C（平台）/ Sheet4!D（直付），费用类型取 Sheet4!B。
     */
    private static final Map<String, Map<String, List<String>>> OPTIONS = Map.of(
            "PLATFORM", Map.of(
                    "SUBD_TYPE", List.of("单会立项", "系列会立项", "系列单场立项"),
                    "OUR_COMPANY", List.of("河南景帅", "郑州华秀", "河南偌君", "河南云展汇", "上海独家记忆", "北京际格")),
            "DIRECT_PAY", Map.of(
                    "SUBD_TYPE", List.of("有合同", "无合同"),
                    "OUR_COMPANY", List.of("景帅", "华秀", "偌君", "会信盈"),
                    "EXPENSE_TYPE", List.of("会议费", "设计费", "物料费", "其他")));

    /**
     * 旧值别名（历史保存的简称 → 现选项），按项目类型 + 选项组限定：
     * 仅平台类我方公司曾以简称保存，直付类我方公司本身就是简称，两种口径不可混用。
     */
    private static final Map<String, Map<String, Map<String, String>>> LEGACY_ALIASES = Map.of(
            "PLATFORM", Map.of("OUR_COMPANY", Map.of("景帅", "河南景帅", "华秀", "郑州华秀", "偌君", "河南偌君")));

    /**
     * 模板与本定义之间已登记的差异（2026-11-26 P2-21 取证结论）。
     * 差异项已提交负责人确认，未确认前保持现状（以本定义为准），由对齐测试显式登记以免新漂移被掩盖。
     */
    static final List<String> KNOWN_TEMPLATE_DEVIATIONS = List.of(
            "平台类我方公司第 5 项：模板 Sheet1!E3 下拉为「上海独家记亿」，本定义为「上海独家记忆」（疑模板笔误，待负责人确认）",
            "直付类我方公司：模板 Sheet2!D3 数据格下拉为 6 项全称，同列 D1:D2/D4+ 与 Sheet4!A 为 4 项简称（模板自相矛盾），本定义取 4 项简称（与需求文档一致）");

    private ProjectFinanceWorkbook() {}

    /** 某项目类型的定义；未配置返回 null。 */
    static Schema schemaOf(String projectType) {
        return projectType == null ? null : SCHEMAS.get(projectType);
    }

    /** 某项目类型的下拉选项（组名 → 取值列表）；未配置返回空 Map。 */
    static Map<String, List<String>> optionsOf(String projectType) {
        Schema schema = schemaOf(projectType);
        if (schema == null) {
            return Map.of();
        }
        Map<String, List<String>> byType = OPTIONS.get(schema.projectType());
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (Field field : schema.fields()) {
            String group = field.optionsGroup();
            if (group == null || options.containsKey(group)) {
                continue;
            }
            List<String> values = byType == null ? null : byType.get(group);
            if (values != null) {
                options.put(group, values);
            }
        }
        return Map.copyOf(options);
    }

    /**
     * 对外结构（前端渲染依据）：分区 → 字段（含中文名、必填口径、下拉取值）。
     * 下拉取值已按字段展开，前端无需再维护选项副本。
     */
    static Map<String, Object> schemaPayload(String projectType) {
        Schema schema = schemaOf(projectType);
        if (schema == null) {
            return Map.of();
        }
        Map<String, List<String>> options = optionsOf(projectType);
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Section section : schema.sections()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Field field : section.fields()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", field.key());
                item.put("label", field.label());
                item.put("valueType", field.valueType());
                item.put("required", field.required());
                item.put("requiredWhenCompanyIs", field.requiredWhenCompanyIs());
                item.put("unit", field.unit());
                item.put("options", field.optionsGroup() == null
                        ? List.of() : options.getOrDefault(field.optionsGroup(), List.of()));
                fields.add(item);
            }
            Map<String, Object> sectionItem = new LinkedHashMap<>();
            sectionItem.put("key", section.key());
            sectionItem.put("title", section.title());
            sectionItem.put("note", section.note());
            sectionItem.put("fields", fields);
            sections.add(sectionItem);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectType", schema.projectType());
        payload.put("sections", sections);
        return payload;
    }

    /**
     * 历史简称 → 现选项 的转换（读回旧数据时用）。
     *
     * @return 命中别名时返回现选项，否则原样返回
     */
    static String resolveLegacyAlias(String projectType, String optionsGroup, String value) {
        if (value == null || value.isBlank() || optionsGroup == null) {
            return value;
        }
        Map<String, Map<String, String>> byType = LEGACY_ALIASES.get(projectType);
        Map<String, String> aliases = byType == null ? null : byType.get(optionsGroup);
        if (aliases == null) {
            return value;
        }
        String resolved = aliases.get(value);
        return resolved != null ? resolved : value;
    }

    /**
     * 归一化已存的下拉值（读回历史数据时用）：旧简称转现选项；不在选项集内的值置空（提示用户重选）。
     * 非下拉字段不受影响。
     */
    static void normalizeStoredSelects(String projectType, Map<String, Object> fields) {
        Schema schema = schemaOf(projectType);
        if (schema == null) {
            return;
        }
        Map<String, List<String>> options = optionsOf(projectType);
        for (Field field : schema.fields()) {
            if (field.optionsGroup() == null) {
                continue;
            }
            Object value = fields.get(field.key());
            if (!(value instanceof String text) || text.isBlank()) {
                continue;
            }
            List<String> allowed = options.getOrDefault(field.optionsGroup(), List.of());
            if (allowed.contains(text)) {
                continue;
            }
            String resolved = resolveLegacyAlias(projectType, field.optionsGroup(), text);
            fields.put(field.key(), allowed.contains(resolved) ? resolved : "");
        }
    }

    /**
     * 服务端必填校验（2026-11-26 新需求 3）：返回缺失的必填字段<b>中文名</b>列表。
     *
     * <p>条件必填（申请表「恒瑞需填写所有信息」分支）同样纳入判定；与前端 el-form rules
     * 共用 {@link Field#isRequired(String)} 这一套定义，前后端口径一致。
     *
     * <p>「已填写」判定：文本字段非空白串；数值字段为数值（null / 空白串视为未填写）。
     */
    static List<String> missingRequiredFields(String projectType, Map<String, Object> values) {
        Schema schema = schemaOf(projectType);
        if (schema == null) {
            return List.of();
        }
        String companyName = values.get("companyName") instanceof String text ? text : null;
        List<String> missing = new ArrayList<>();
        for (Field field : schema.fields()) {
            if (!field.isRequired(companyName)) {
                continue;
            }
            Object value = values.get(field.key());
            boolean filled = value instanceof String text ? !text.isBlank() : value instanceof Number;
            if (!filled) {
                missing.add(field.label());
            }
        }
        return List.copyOf(missing);
    }

    /**
     * 按项目类型白名单清洗表单数据（2026-11-26 P1-9 / P2-13）。
     * 白名单＝该类型字段定义（不再整包落库）；白名单内字段类型不符时抛业务异常，文案只给中文字段名。
     * 空值（null / 空白串）视为「未填写」：数值字段不再被写成文本单元格（P2-19）。
     * 契约：不修改入参，返回新 Map。
     */
    static Map<String, Object> sanitize(String projectType, Map<String, Object> raw) {
        Schema schema = schemaOf(projectType);
        if (schema == null) {
            throw new BusinessException("请先选择项目类型");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Field field : schema.fields()) {
            if (!raw.containsKey(field.key())) {
                continue;
            }
            Object value = raw.get(field.key());
            if (value == null) {
                continue;
            }
            if (TYPE_NUMBER.equals(field.valueType())) {
                if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0) {
                    result.put(field.key(), value);
                    continue;
                }
                if (value instanceof String text && text.isBlank()) {
                    continue;
                }
                throw new BusinessException("「" + field.label() + "」应为不小于 0 的金额");
            }
            if (value instanceof String text) {
                result.put(field.key(), text);
                continue;
            }
            throw new BusinessException("「" + field.label() + "」格式不正确");
        }
        return result;
    }

    /**
     * 生成合规立项申请表（按项目类型选 sheet，写入数据行第 3 行）。
     * 列位置由字段定义顺序派生，与模板表头一一对应。
     */
    static byte[] generate(String projectType, Map<String, Object> values) {
        Schema schema = schemaOf(projectType);
        if (schema == null) {
            throw new BusinessException("请先选择项目类型");
        }
        for (Field field : schema.fields()) {
            if (field.required()) {
                requireText(values, field);
            }
        }
        try (var template = ProjectFinanceWorkbook.class.getResourceAsStream("/templates/project-finance.xlsx");
             var workbook = new XSSFWorkbook(template);
             var result = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheet(schema.sheet());
            for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
                String name = workbook.getSheetName(i);
                if (!name.equals(schema.sheet()) && !name.equals("Sheet4")) {
                    workbook.removeSheetAt(i);
                }
            }
            workbook.setActiveSheet(workbook.getSheetIndex(sheet));
            var row = sheet.getRow(2);
            if (row == null) {
                row = sheet.createRow(2);
            }
            row.getCell(0, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(1);
            row.getCell(1, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(schema.sheet());
            List<Field> fields = schema.fields();
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                Object value = values.get(field.key());
                if (value == null) {
                    continue;
                }
                var cell = row.getCell(i + 2, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (TYPE_NUMBER.equals(field.valueType())) {
                    if (value instanceof Number amount && Double.isFinite(amount.doubleValue()) && amount.doubleValue() >= 0) {
                        cell.setCellValue(amount.doubleValue());
                    }
                    // 非数值（空串等）视为未填写：保留模板空白单元格，不写入文本（P2-19）
                } else if (field.excelDate() && value instanceof String text) {
                    // 2026-11-26 P3-22：单日会议写 Excel 原生日期（可排序/筛选/计算）；
                    // 跨日会议保留区间文本（单列装不下两个原生日期，丢结束日会损失合规数据）
                    LocalDate date = parseIsoDate(text);
                    if (date != null) {
                        writeDateCell(workbook, cell, date);
                    } else {
                        cell.setCellValue(text);
                    }
                } else if (value instanceof String text && text.length() <= 500) {
                    cell.setCellValue(text);
                } else {
                    throw new BusinessException("财务表单字段格式错误：" + field.label());
                }
            }
            workbook.write(result);
            return result.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成合规立项申请表失败");
        }
    }

    private static void requireText(Map<String, Object> values, Field field) {
        if (!(values.get(field.key()) instanceof String text) || text.isBlank()) {
            throw new BusinessException("请填写财务表单必填项：" + field.label());
        }
    }

    /** 解析 ISO 日期（yyyy-MM-dd）；非该格式（如跨日区间文本）返回 null。 */
    private static LocalDate parseIsoDate(String text) {
        String value = text.trim();
        if (value.length() != 10 || value.charAt(4) != '-' || value.charAt(7) != '-') {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 写 Excel 原生日期：模板的日期列自带 {@code yyyy/m/d;@} 格式（平台类 H3 / 直付类 H3）；
     * 重点类的日期列无日期格式，需补一个，否则 Excel 会显示日期序列号。
     */
    private static void writeDateCell(XSSFWorkbook workbook, Cell cell, LocalDate date) {
        cell.setCellValue(date);
        CellStyle style = cell.getCellStyle();
        String format = style.getDataFormatString();
        if (format == null || !(format.contains("y") || format.contains("m") || format.contains("d"))) {
            CellStyle dated = workbook.createCellStyle();
            dated.cloneStyleFrom(style);
            dated.setDataFormat(workbook.createDataFormat().getFormat("yyyy/m/d"));
            cell.setCellStyle(dated);
        }
    }
}
