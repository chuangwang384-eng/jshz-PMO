package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import org.apache.poi.ss.usermodel.BorderStyle;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目成本单（2026-11-25 负责人要求：结算资料「成本单制作」由上传 PDF 改为填表后自动生成 Excel）。
 *
 * <p>表结构对齐负责人提供的《项目成本单模板》：标题 + 项目/客户信息 + 8 个费用分节（酒店、交通、媒体、
 * 搭建、物料、人员、劳务、转化费）+ 分节小计 + 本项目成本费用共计。列：项目 / 明细 / 单价 / 单位 /
 * 数量 / 应付金额 / 结算金额 / 退款金额 / 付款方式 / 供应商 / 备注。</p>
 *
 * <p>金额口径：应付金额 = 单价 × 数量；退款金额 = 结算金额 − 应付金额。生成时写入 POI 公式并置
 * calcPr fullCalcOnLoad，保证用 Excel 打开即算；同时在服务端把计算结果写进单元格底值不可行
 * （POI 公式无缓存值），因此页脚注明"打开后自动重算"。</p>
 *
 * <p>分节与费用项目录为唯一真源：前端表单由 {@link #catalog()} 渲染，提交时按 key 回填，
 * 前端不再复制一份费用项清单，避免两侧漂移。</p>
 */
final class ProjectCostSheetWorkbook {

    static final String SLOT = "U08";

    private static final String TITLE = "项目结算成本";
    private static final String[] HEADERS = {
            "项目", "明细", "单价", "单位", "数量",
            "应付金额（元）", "结算金额（元）", "退款金额（元）", "付款方式", "供应商", "备注"};

    private static final String MONEY_FMT = "#,##0.00";
    /** 明细金额：零值显示为「-」，减少满屏 0.00 造成的噪声（只影响显示，不改单元格数值）。 */
    private static final String MONEY_CELL_FMT = "#,##0.00;[Red]-#,##0.00;\"-\"";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");

    /** 费用项定义：明细为空表示单行项（模板中的"项目"列即费用项名）。 */
    record Item(String key, String name, String detail, String unit) {}

    /** 费用分节定义。 */
    record Section(String key, String label, List<Item> items) {}

    private static final List<Section> SECTIONS = List.of(
            new Section("HOTEL", "1、酒店费用（酒店内产生的住宿、用餐、茶歇、会场等）", List.of(
                    new Item("HOTEL-1", "住宿", "", "元/间夜"),
                    new Item("HOTEL-2", "酒水", "", "元/箱"),
                    new Item("HOTEL-3", "会场费", "", "元/场"),
                    new Item("HOTEL-4", "茶歇", "", "元/次"),
                    new Item("HOTEL-5", "税费", "", "元/次"))),
            new Section("TRAFFIC", "2、交通费用（会议涉及大小交通等）", List.of(
                    new Item("TRAFFIC-1", "专家高铁费用", "", "元/次"),
                    new Item("TRAFFIC-2", "专家租车费用", "", "元/次"),
                    new Item("TRAFFIC-3", "专家租车税费", "", "元/次"),
                    new Item("TRAFFIC-4", "专家小交通费用", "", "元/次"))),
            new Section("MEDIA", "3、媒体费用（摄影、摄像、线上系统）", List.of(
                    new Item("MEDIA-1", "摄影", "", "元/次"),
                    new Item("MEDIA-2", "摄影税费", "", "元/次"),
                    new Item("MEDIA-3", "视频制作", "", "元/次"),
                    new Item("MEDIA-4", "视频制作税费", "", "元/次"))),
            new Section("BUILD", "4、搭建设备类费用（屏幕、灯光、音响、展架、舞台、花束、讲台包装、控台遮挡等特装）", List.of(
                    new Item("BUILD-1", "屏幕", "", "元/次"),
                    new Item("BUILD-2", "灯光", "", "元/次"),
                    new Item("BUILD-3", "音响", "", "元/次"),
                    new Item("BUILD-4", "税点", "", "元/次"))),
            new Section("MATERIAL", "5、打印类小型物料（日程、台卡、手册等打印类物料）", List.of(
                    new Item("MATERIAL-1", "贴纸", "", "元/条"),
                    new Item("MATERIAL-2", "台卡", "", "元/张"),
                    new Item("MATERIAL-3", "卡纸", "", "元/个"),
                    new Item("MATERIAL-4", "台卡", "", "元/个"),
                    new Item("MATERIAL-5", "椅子贴", "", "元/个"),
                    new Item("MATERIAL-6", "日程", "", "元/张"),
                    new Item("MATERIAL-7", "日程", "", "元/张"),
                    new Item("MATERIAL-8", "税点", "", "元/次"))),
            new Section("STAFF", "6、人员费用（我司人员跟进会议所产生的交通、住宿、用餐等费用）", List.of(
                    new Item("STAFF-1", "执行人员费用", "住宿费", "元/间夜"),
                    new Item("STAFF-2", "执行人员费用", "交通费", "元/次"))),
            new Section("LABOR", "7、劳务费用", List.of(
                    new Item("LABOR-1", "劳务费用", "", "元/场"),
                    new Item("LABOR-2", "劳务费用税点", "", "元/次"))),
            new Section("CONVERT", "8、转化费", List.of(
                    new Item("CONVERT-1", "转化费", "", "元"))));

    private ProjectCostSheetWorkbook() {
    }

    /** 表单目录（分节 + 费用项），供前端渲染；不含任何项目数据。 */
    static List<Map<String, Object>> catalog() {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Section section : SECTIONS) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Item item : section.items()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", item.key());
                row.put("name", item.name());
                row.put("detail", item.detail());
                row.put("unit", item.unit());
                items.add(row);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", section.key());
            row.put("label", section.label());
            row.put("items", items);
            sections.add(row);
        }
        return sections;
    }

    /** 表头默认值，取自项目档案；用户可在表单里改。 */
    static Map<String, Object> defaults(Project project) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectName", project.getName() == null ? "" : project.getName());
        m.put("projectTime", dateRange(project));
        m.put("customerName", project.getClientCompany() == null ? "" : project.getClientCompany());
        m.put("projectLocation", "");
        m.put("customerOwner", project.getClientName() == null ? "" : project.getClientName());
        m.put("projectOwner", "");
        m.put("projectAssistant", "");
        return m;
    }

    private static String dateRange(Project project) {
        LocalDate from = project.getMeetingStartDate() != null ? project.getMeetingStartDate() : project.getStartDate();
        LocalDate to = project.getMeetingEndDate() != null ? project.getMeetingEndDate() : project.getEndDate();
        if (from == null && to == null) return "";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (from == null) return to.format(fmt);
        if (to == null || to.equals(from)) return from.format(fmt);
        return from.format(fmt) + " 至 " + to.format(fmt);
    }

    /**
     * 校验并归一化提交的表单：表头按 200 字截断校验，行金额必须为非负数字且不超过 1 亿，
     * 至少填写一项金额。返回的结构与 {@link #generate} 的入参一致。
     */
    static Map<String, Object> normalize(Map<String, Object> body, Project project) {
        Map<String, Object> source = body == null ? Map.of() : body;
        Map<String, Object> form = new LinkedHashMap<>();
        Map<String, Object> fallback = defaults(project);
        for (String field : List.of("projectName", "projectTime", "customerName", "projectLocation",
                "customerOwner", "projectOwner", "projectAssistant")) {
            String value = text(source.get(field));
            if (value.isEmpty()) value = text(fallback.get(field));
            if (value.length() > 200) throw new BusinessException("表头字段不能超过 200 个字符");
            form.put(field, value);
        }

        Map<String, Object> submitted = new LinkedHashMap<>();
        Object rawSections = source.get("sections");
        if (rawSections instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> section)) continue;
                Object key = section.get("key");
                Object items = section.get("items");
                if (key == null || !(items instanceof List<?> rows)) continue;
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> item && item.get("key") != null) {
                        submitted.put(String.valueOf(item.get("key")), item);
                    }
                }
            }
        }

        boolean anyAmount = false;
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Section section : SECTIONS) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Item item : section.items()) {
                Map<?, ?> raw = (Map<?, ?>) submitted.get(item.key());
                BigDecimal price = amount(raw == null ? null : raw.get("price"), item.name() + "单价");
                BigDecimal qty = amount(raw == null ? null : raw.get("qty"), item.name() + "数量");
                BigDecimal settle = amount(raw == null ? null : raw.get("settle"), item.name() + "结算金额");
                BigDecimal payable = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                BigDecimal refund = settle.subtract(payable);
                if (payable.signum() != 0 || settle.signum() != 0) anyAmount = true;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", item.key());
                row.put("name", item.name());
                row.put("detail", item.detail());
                row.put("unit", item.unit());
                row.put("price", price);
                row.put("qty", qty);
                row.put("payable", payable);
                row.put("settle", settle);
                row.put("refund", refund);
                row.put("payMethod", shortText(raw == null ? null : raw.get("payMethod"), "付款方式"));
                row.put("supplier", shortText(raw == null ? null : raw.get("supplier"), "供应商"));
                row.put("remark", shortText(raw == null ? null : raw.get("remark"), "备注"));
                items.add(row);
            }
            Map<String, Object> sectionRow = new LinkedHashMap<>();
            sectionRow.put("key", section.key());
            sectionRow.put("label", section.label());
            sectionRow.put("items", items);
            sections.add(sectionRow);
        }
        if (!anyAmount) {
            throw new BusinessException("成本单至少需要填写一项费用金额");
        }
        form.put("sections", sections);
        return form;
    }

    private static BigDecimal amount(Object raw, String label) {
        if (raw == null || String.valueOf(raw).isBlank()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal value;
        try {
            value = new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(label + "必须为数字");
        }
        if (value.signum() < 0) throw new BusinessException(label + "不能为负数");
        if (value.compareTo(MAX_AMOUNT) > 0) throw new BusinessException(label + "不能超过 1 亿");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private static String shortText(Object raw, String label) {
        String value = text(raw);
        if (value.length() > 200) throw new BusinessException(label + "不能超过 200 个字符");
        return value;
    }

    /**
     * 按归一化后的表单生成 xlsx（单表「项目结算成本」）。
     *
     * <p>2026-11-25 版式美化：列宽按中文两字宽重新分配、表头信息区改为「标签+合并值区」三行、
     * 分节/小计/合计分层次配色、金额与数量右对齐、明细零值显示为「-」、表尾说明独占整行并自动换行，
     * 另加打印区域、页宽适应与表头重复。数据值与公式口径不变：F=C×E、H=G−F、分节 SUM、总计为分节小计之和。</p>
     */
    @SuppressWarnings("unchecked")
    static byte[] generate(Map<String, Object> form) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var calcPr = wb.getCTWorkbook().getCalcPr();
            if (calcPr == null) calcPr = wb.getCTWorkbook().addNewCalcPr();
            calcPr.setCalcId(0);
            calcPr.setFullCalcOnLoad(true);

            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet("成本");
            sheet.setDisplayGridlines(false);
            sheet.setDefaultRowHeightInPoints(20);

            // 列宽按「11 列铺满 A4 横向」分配：项目/明细给足中文宽度，避免名称折行导致行高参差
            int[] widths = {18, 15, 13, 12, 9, 16, 16, 16, 14, 20, 20};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);

            int r = 0;
            Row title = sheet.createRow(r);
            title.setHeightInPoints(34);
            block(sheet, title, 0, 10, TITLE, styles.title);
            r++;

            // 表头信息区：标签格 + 合并值区（替代原先「标签紧跟值」导致中文标签挤在窄列里的排法）
            r = infoRow(sheet, r, styles,
                    new Object[]{"项目名称", str(form.get("projectName")), 0, 0, 1, 4},
                    new Object[]{"项目时间", str(form.get("projectTime")), 5, 5, 6, 10});
            r = infoRow(sheet, r, styles,
                    new Object[]{"客户名称", str(form.get("customerName")), 0, 0, 1, 4},
                    new Object[]{"项目地点", str(form.get("projectLocation")), 5, 5, 6, 10});
            r = infoRow(sheet, r, styles,
                    new Object[]{"客户负责人", str(form.get("customerOwner")), 0, 0, 1, 2},
                    new Object[]{"项目负责人", str(form.get("projectOwner")), 3, 3, 4, 6},
                    new Object[]{"项目协助人", str(form.get("projectAssistant")), 7, 7, 8, 10});

            Row header = sheet.createRow(r);
            header.setHeightInPoints(30);
            for (int i = 0; i < HEADERS.length; i++) cell(header, i, HEADERS[i], styles.head);
            int headerRow = r;
            r++;

            List<Integer> subtotalRows = new ArrayList<>();
            List<?> sections = (List<?>) form.get("sections");
            for (Object entry : sections == null ? List.of() : sections) {
                Map<String, Object> section = (Map<String, Object>) entry;
                Row sectionRow = sheet.createRow(r);
                sectionRow.setHeightInPoints(24);
                block(sheet, sectionRow, 0, 10, str(section.get("label")), styles.section);
                r++;

                int first = r;
                List<?> items = (List<?>) section.get("items");
                for (Object rowObj : items == null ? List.of() : items) {
                    Map<String, Object> item = (Map<String, Object>) rowObj;
                    Row row = sheet.createRow(r);
                    row.setHeightInPoints(20);
                    cell(row, 0, str(item.get("name")), styles.name);
                    cell(row, 1, str(item.get("detail")), styles.text);
                    cell(row, 2, item.get("price"), styles.money);
                    cell(row, 3, str(item.get("unit")), styles.unit);
                    cell(row, 4, item.get("qty"), styles.qty);
                    cellFormula(row, 5, "=C" + (r + 1) + "*E" + (r + 1), styles.money);
                    cell(row, 6, item.get("settle"), styles.money);
                    cellFormula(row, 7, "=G" + (r + 1) + "-F" + (r + 1), styles.money);
                    cell(row, 8, str(item.get("payMethod")), styles.text);
                    cell(row, 9, str(item.get("supplier")), styles.text);
                    cell(row, 10, str(item.get("remark")), styles.text);
                    r++;
                }
                Row subtotal = sheet.createRow(r);
                subtotal.setHeightInPoints(22);
                block(sheet, subtotal, 0, 4, "此类目费用合计：", styles.subTotalLabel);
                int from = first + 1;
                cellFormula(subtotal, 5, "=SUM(F" + from + ":F" + r + ")", styles.subTotalMoney);
                cellFormula(subtotal, 6, "=SUM(G" + from + ":G" + r + ")", styles.subTotalMoney);
                cellFormula(subtotal, 7, "=SUM(H" + from + ":H" + r + ")", styles.subTotalMoney);
                for (int col = 8; col <= 10; col++) cell(subtotal, col, "", styles.subTotal);
                subtotalRows.add(r);
                r++;
            }

            Row total = sheet.createRow(r);
            total.setHeightInPoints(30);
            block(sheet, total, 0, 4, "本项目成本费用共计", styles.grandLabel);
            String[] columns = {"F", "G", "H"};
            int[] targets = {5, 6, 7};
            for (int i = 0; i < columns.length; i++) {
                String formula = subtotalFormula(subtotalRows, columns[i]);
                if (formula == null) {
                    cell(total, targets[i], BigDecimal.ZERO, styles.grandMoney);
                } else {
                    cellFormula(total, targets[i], formula, styles.grandMoney);
                }
            }
            for (int col = 8; col <= 10; col++) cell(total, col, "", styles.grandMoney);
            r += 2;

            Row note = sheet.createRow(r);
            note.setHeightInPoints(38);
            block(sheet, note, 0, 10,
                    "本表由系统按《项目成本单模板》生成：应付金额=单价×数量，退款金额=结算金额−应付金额，"
                            + "分节合计与总计均为单元格公式，改动单价/数量/结算金额后自动重算。",
                    styles.note);

            sheet.createFreezePane(0, headerRow + 1);
            sheet.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));
            wb.setPrintArea(0, 0, 10, 0, r);
            sheet.setFitToPage(true);
            var print = sheet.getPrintSetup();
            print.setLandscape(true);
            print.setPaperSize((short) 9); // 9 = A4
            print.setFitWidth((short) 1);
            print.setFitHeight((short) 0);
            sheet.setMargin(Sheet.LeftMargin, 0.4);
            sheet.setMargin(Sheet.RightMargin, 0.4);
            sheet.setMargin(Sheet.TopMargin, 0.5);
            sheet.setMargin(Sheet.BottomMargin, 0.5);
            sheet.setHorizontallyCenter(true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("成本单 Excel 生成失败：" + e.getMessage());
        }
    }

    /** 总计公式：把各分节小计单元格相加（保持可重算，避免写死服务端算出的静态值）。 */
    private static String subtotalFormula(List<Integer> rows, String column) {
        if (rows.isEmpty()) return null;
        List<String> refs = new ArrayList<>();
        for (int row : rows) refs.add(column + (row + 1));
        return "=SUM(" + String.join(",", refs) + ")";
    }

    /**
     * 表头信息行：每项 {标签, 值, 标签起始列, 标签终止列, 值起始列, 值终止列}。
     * 合并区内的每个单元格都写样式，避免合并后边框缺失。
     */
    private static int infoRow(Sheet sheet, int r, Styles styles, Object[]... specs) {
        Row row = sheet.createRow(r);
        row.setHeightInPoints(22);
        for (Object[] spec : specs) {
            int labelFrom = (Integer) spec[2];
            int labelTo = (Integer) spec[3];
            int valueFrom = (Integer) spec[4];
            int valueTo = (Integer) spec[5];
            block(sheet, row, labelFrom, labelTo, String.valueOf(spec[0]), styles.infoLabel);
            block(sheet, row, valueFrom, valueTo, String.valueOf(spec[1]), styles.infoValue);
        }
        return r + 1;
    }

    /** 写入并合并一个区块：范围内每格都套样式（合并单元格的边框取各格样式），首格放值。 */
    private static void block(Sheet sheet, Row row, int from, int to, String value, CellStyle style) {
        for (int col = from; col <= to; col++) {
            var target = row.createCell(col);
            target.setCellStyle(style);
            if (col == from) target.setCellValue(value == null ? "" : value);
        }
        if (to > from) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), from, to));
        }
    }

    private static void cell(Row row, int col, Object value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    private static void cellFormula(Row row, int col, String formula, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellStyle(style);
        cell.setCellFormula(formula.substring(1));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 每次生成独立样式集合（POI 样式只能属于同一个工作簿）。 */
    private static final class Styles {
        final CellStyle title;
        final CellStyle head;
        final CellStyle infoLabel;
        final CellStyle infoValue;
        final CellStyle section;
        final CellStyle name;
        final CellStyle text;
        final CellStyle unit;
        final CellStyle money;
        final CellStyle qty;
        final CellStyle subTotalLabel;
        final CellStyle subTotal;
        final CellStyle subTotalMoney;
        final CellStyle grandLabel;
        final CellStyle grandMoney;
        final CellStyle note;

        Styles(XSSFWorkbook wb) {
            var formats = wb.createDataFormat();

            Font titleFont = font(wb, 16, true, IndexedColors.BLACK);
            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font headFont = font(wb, 11, true, IndexedColors.BLACK);
            head = bordered(wb, headFont);
            head.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setAlignment(HorizontalAlignment.CENTER);
            head.setWrapText(true);
            head.setVerticalAlignment(VerticalAlignment.CENTER);

            infoLabel = bordered(wb, font(wb, 10, true, IndexedColors.BLACK));
            infoLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            infoLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            infoLabel.setAlignment(HorizontalAlignment.CENTER);
            infoLabel.setVerticalAlignment(VerticalAlignment.CENTER);

            infoValue = bordered(wb, font(wb, 10, false, IndexedColors.BLACK));
            infoValue.setAlignment(HorizontalAlignment.LEFT);
            infoValue.setVerticalAlignment(VerticalAlignment.CENTER);
            infoValue.setIndention((short) 1);
            infoValue.setWrapText(false);

            section = bordered(wb, font(wb, 10, true, IndexedColors.BLACK));
            section.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section.setAlignment(HorizontalAlignment.LEFT);
            section.setVerticalAlignment(VerticalAlignment.CENTER);
            section.setIndention((short) 1);

            Font plain = font(wb, 10, false, IndexedColors.BLACK);
            name = bordered(wb, plain);
            name.setAlignment(HorizontalAlignment.LEFT);
            name.setVerticalAlignment(VerticalAlignment.CENTER);
            name.setIndention((short) 1);
            name.setWrapText(false);

            text = bordered(wb, plain);
            text.setAlignment(HorizontalAlignment.LEFT);
            text.setVerticalAlignment(VerticalAlignment.CENTER);
            text.setIndention((short) 1);
            text.setWrapText(false);

            unit = bordered(wb, plain);
            unit.setAlignment(HorizontalAlignment.CENTER);
            unit.setVerticalAlignment(VerticalAlignment.CENTER);

            money = bordered(wb, plain);
            money.setDataFormat(formats.getFormat(MONEY_CELL_FMT));
            money.setAlignment(HorizontalAlignment.RIGHT);
            money.setVerticalAlignment(VerticalAlignment.CENTER);

            // 数量保持常规数字格式：避免「#,##0.##」在 Excel 里显示成「2.」
            qty = bordered(wb, plain);
            qty.setAlignment(HorizontalAlignment.CENTER);
            qty.setVerticalAlignment(VerticalAlignment.CENTER);

            Font bold = font(wb, 10, true, IndexedColors.BLACK);
            subTotalLabel = bordered(wb, bold);
            subTotalLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            subTotalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            subTotalLabel.setAlignment(HorizontalAlignment.RIGHT);
            subTotalLabel.setVerticalAlignment(VerticalAlignment.CENTER);

            subTotal = bordered(wb, bold);
            subTotal.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            subTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            subTotalMoney = bordered(wb, bold);
            subTotalMoney.setDataFormat(formats.getFormat(MONEY_FMT));
            subTotalMoney.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            subTotalMoney.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            subTotalMoney.setAlignment(HorizontalAlignment.RIGHT);
            subTotalMoney.setVerticalAlignment(VerticalAlignment.CENTER);

            Font grandFont = font(wb, 11, true, IndexedColors.WHITE);
            grandLabel = bordered(wb, grandFont);
            grandLabel.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            grandLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            grandLabel.setAlignment(HorizontalAlignment.RIGHT);
            grandLabel.setVerticalAlignment(VerticalAlignment.CENTER);

            grandMoney = bordered(wb, grandFont);
            grandMoney.setDataFormat(formats.getFormat(MONEY_FMT));
            grandMoney.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            grandMoney.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            grandMoney.setAlignment(HorizontalAlignment.RIGHT);
            grandMoney.setVerticalAlignment(VerticalAlignment.CENTER);

            note = wb.createCellStyle();
            note.setWrapText(true);
            note.setAlignment(HorizontalAlignment.LEFT);
            note.setVerticalAlignment(VerticalAlignment.TOP);
            note.setIndention((short) 1);
            Font noteFont = font(wb, 9, false, IndexedColors.GREY_50_PERCENT);
            noteFont.setItalic(true);
            note.setFont(noteFont);
        }

        private static Font font(XSSFWorkbook wb, int size, boolean bold, IndexedColors color) {
            Font font = wb.createFont();
            font.setFontName("微软雅黑");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(color.getIndex());
            return font;
        }

        private static CellStyle bordered(XSSFWorkbook wb, Font font) {
            CellStyle style = wb.createCellStyle();
            style.setFont(font);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            return style;
        }
    }
}
