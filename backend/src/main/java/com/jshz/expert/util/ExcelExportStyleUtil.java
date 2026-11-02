package com.jshz.expert.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.util.List;

/**
 * Excel（.xlsx）导出样式统一工具类。
 *
 * <p>为所有 Excel 导出提供一套标准样式，后续新增的 Excel 导出一律复用 {@link #applyStyle(XSSFSheet, List, int)}。</p>
 *
 * <ul>
 *   <li>分列限宽的列宽自适应：中文按 2 单位宽估算，遍历数据行取实际最大内容宽度与列上限的较小值（不使用 POI autoSizeColumn，避免中文字符估宽失真）；</li>
 *   <li>数字/编号列固定宽度且不换行（内容以字符串写入，天然避免科学计数法）；</li>
 *   <li>长文本列自动换行（wrapText），行高不显式设置，由 Excel 打开时按换行内容自动撑高，保证完整展示不截断；</li>
 *   <li>表头加粗 / 浅色底纹 / 边框线 / 居中；</li>
 *   <li>数据单元格统一细边框；长文本列顶部对齐，数字列不换行，其余垂直居中；</li>
 *   <li>冻结首行（createFreezePane(0,1)）与自动筛选（setAutoFilter）。</li>
 * </ul>
 */
public final class ExcelExportStyleUtil {

    private ExcelExportStyleUtil() {
    }

    /** 列配置：描述单列的列名、宽度上限（字符单位）、是否数字列（不换行）、是否长文本列（换行）。 */
    public static final class ColumnConfig {
        private final String headerName;
        private final int maxWidth;
        private final boolean numeric;
        private final boolean longText;

        /**
         * @param headerName 列名（与表头单元格文本对应）
         * @param maxWidth   列宽上限（字符单位，中文按 2 单位计）
         * @param numeric    是否数字/编号列（固定宽度、不换行）
         * @param longText   是否长文本列（自动换行、顶部对齐）
         */
        public ColumnConfig(String headerName, int maxWidth, boolean numeric, boolean longText) {
            this.headerName = headerName;
            this.maxWidth = maxWidth;
            this.numeric = numeric;
            this.longText = longText;
        }

        public String getHeaderName() {
            return headerName;
        }

        public int getMaxWidth() {
            return maxWidth;
        }

        public boolean isNumeric() {
            return numeric;
        }

        public boolean isLongText() {
            return longText;
        }
    }

    /**
     * 对已写完数据的 Excel 工作表应用统一导出样式。
     * <p>调用时机：数据行全部写入完成之后、{@code wb.write} 之前调用。</p>
     *
     * @param sheet           目标工作表
     * @param columnConfigs   每列配置（顺序与工作表列一一对应）
     * @param headerRowIndex  表头所在行索引（0-based，常规为 0）
     */
    public static void applyStyle(XSSFSheet sheet, List<ColumnConfig> columnConfigs, int headerRowIndex) {
        if (sheet == null || columnConfigs == null || columnConfigs.isEmpty()) {
            return;
        }
        XSSFWorkbook wb = sheet.getWorkbook();
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle numberStyle = createNumberStyle(wb);
        CellStyle longTextStyle = createLongTextStyle(wb);
        CellStyle normalStyle = createNormalStyle(wb);

        int colCount = columnConfigs.size();

        // 1. 表头：加粗 / 浅色底纹 / 边框 / 居中
        XSSFRow headerRow = sheet.getRow(headerRowIndex);
        if (headerRow != null) {
            for (int c = 0; c < colCount; c++) {
                XSSFCell cell = headerRow.getCell(c);
                if (cell == null) {
                    cell = headerRow.createCell(c);
                }
                cell.setCellStyle(headerStyle);
            }
        }

        // 2. 数据行：应用单元格样式，并统计各列实际最大内容宽度（中文按 2 单位）
        int[] maxWidths = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            maxWidths[c] = textWidth(columnConfigs.get(c).getHeaderName());
        }
        for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
            XSSFRow row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < colCount; c++) {
                XSSFCell cell = row.getCell(c);
                if (cell == null) {
                    continue;
                }
                ColumnConfig cfg = columnConfigs.get(c);
                cell.setCellStyle(cfg.isNumeric() ? numberStyle : (cfg.isLongText() ? longTextStyle : normalStyle));
                int w = textWidth(cellValue(cell));
                if (w > maxWidths[c]) {
                    maxWidths[c] = w;
                }
            }
        }

        // 3. 列宽：数字/编号列固定（刚好放下且不换行）；其余取 min(实际最大内容宽度, 上限)
        for (int c = 0; c < colCount; c++) {
            ColumnConfig cfg = columnConfigs.get(c);
            int width = cfg.isNumeric() ? cfg.getMaxWidth() : Math.min(maxWidths[c], cfg.getMaxWidth());
            width = Math.max(width, 4);
            sheet.setColumnWidth(c, width * 256);
        }

        // 4. 冻结首行
        sheet.createFreezePane(0, headerRowIndex + 1);

        // 5. 自动筛选（覆盖表头到末行）
        if (sheet.getLastRowNum() >= headerRowIndex) {
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, sheet.getLastRowNum(), 0, colCount - 1));
        }
    }

    // ==================== 样式构建 ====================

    private static CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setThinBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** 数字/编号列：统一细边框、居中、不换行。 */
    private static CellStyle createNumberStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        setThinBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        return style;
    }

    /** 长文本列：统一细边框、自动换行、顶部对齐。 */
    private static CellStyle createLongTextStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        setThinBorders(style);
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    /** 普通列：统一细边框、垂直居中。 */
    private static CellStyle createNormalStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        setThinBorders(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    // ==================== 宽度估算 ====================

    /** 估算文本显示宽度：中文（非 ASCII）按 2 单位，其余按 1 单位。 */
    private static int textWidth(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += (ch > 0xFF) ? 2 : 1;
        }
        return w;
    }

    /** 读取单元格文本值（数字还原为不带科学计数的原文），用于宽度统计。 */
    private static String cellValue(XSSFCell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        switch (type) {
            case STRING:
                return cell.getStringCellValue() == null ? "" : cell.getStringCellValue();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    return BigDecimal.valueOf(v).toBigInteger().toString();
                }
                return String.valueOf(v);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}
