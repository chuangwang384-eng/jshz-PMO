package com.jshz.expert.service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recognizes ruled tables by matching OCR word coordinates to raster grid cells.
 *
 * <p>20261123（需求20）：列数不再写死 4 条竖线。按表头语义识别「项目/事项」列与「标准/内容」列，
 * 支持 2~6 个内容列（如 3 列、5 列）；4 竖线的标准格式保持原识别结果不变。
 * 表头无法判读且不是标准格式时返回空，避免生成错误的事项草稿。</p>
 */
final class ComplianceOcrTable {
    private record Word(int x, int y, String line, String text) {}

    /**
     * 栅格：rows/columns 为横竖线坐标。titleColumn/bodyColumn 为单元格索引
     * （单元格 i 横跨 columns[i]..columns[i+1]），-1 表示尚未判读。
     */
    record Grid(List<Integer> rows, List<Integer> columns, int titleColumn, int bodyColumn) {
        Grid(List<Integer> rows, List<Integer> columns) {
            this(rows, columns, -1, -1);
        }

        Grid withCellColumns(int title, int body) {
            return new Grid(rows, columns, title, body);
        }

        boolean hasLayout() {
            return titleColumn > 0 && bodyColumn > titleColumn;
        }
    }

    /** 识别结果：事项块 + 已判读列位的栅格（refineRows 复用同一列映射，避免表头重读）。 */
    record Table(List<KnowledgeDocTextExtractor.SourceBlock> blocks, Grid grid) {
        static Table empty(Grid grid) {
            return new Table(List.of(), grid);
        }

        boolean isEmpty() {
            return blocks.isEmpty();
        }
    }

    static List<KnowledgeDocTextExtractor.SourceBlock> refineRows(
            BufferedImage image, Grid grid, List<KnowledgeDocTextExtractor.SourceBlock> blocks) {
        var result = new ArrayList<KnowledgeDocTextExtractor.SourceBlock>();
        int row = 0;
        int retries = 0;
        // 20261123 修复：直接递增行号会因 PAGE/PDF_PAGE 块与 TABLE_ROW 块混排而错位，
        // 改为从锚点「表格第 N 项」解析真实行号，并先判越界再取单元格坐标
        java.util.regex.Pattern rowIndex = java.util.regex.Pattern.compile("表格第(\\d+)项$");
        for (var block : blocks) {
            var match = rowIndex.matcher(block.anchor());
            if (!match.find()) {
                result.add(block);
                continue;
            }
            row = Integer.parseInt(match.group(1));
            if (!"TABLE_ROW".equals(block.kind())) {
                result.add(block);
                continue;
            }
            String[] cells = block.text().split(" \\| ", 2);
            for (int cell = 0; cell < cells.length; cell++) {
                String original = cells[cell];
                if (original.lines().noneMatch(ComplianceOcrTable::looksCorrupt)) continue;
                // 需求20：单元格 → 列线索引由表头判读结果给出（标准格式为 1/2），不再写死 cell+1/cell+2
                int column = columnOf(grid, cell);
                if (retries < 6 && column > 0
                        && row + 1 < grid.rows().size() && column + 1 < grid.columns().size()) {
                    retries++;
                    int left = grid.columns().get(column) + 3;
                    int top = grid.rows().get(row) + 3;
                    int width = grid.columns().get(column + 1) - left - 3;
                    int height = grid.rows().get(row + 1) - top - 3;
                    if (width > 0 && height > 0) {
                        String retry = KnowledgeDocTextExtractor.ocrPage(image.getSubimage(left, top, width, height), false).strip();
                        if (!retry.isEmpty() && retry.lines().noneMatch(ComplianceOcrTable::looksCorrupt)
                                && retry.lines().count() >= original.lines().count()) {
                            cells[cell] = retry;
                            continue;
                        }
                    }
                }
                cells[cell] = String.join("\n", original.lines()
                        .map(line -> looksCorrupt(line) ? "〔此行识别不清，请核对原文〕" : line).toList());
            }
            result.add(new KnowledgeDocTextExtractor.SourceBlock(block.anchor(), String.join(" | ", cells), block.kind()));
        }
        return result;
    }

    /** 文本单元格序号（0=标题, 1=正文）对应的列线索引；未知返回 -1。 */
    private static int columnOf(Grid grid, int cell) {
        if (grid == null) return -1;
        // 未判读列位时按标准格式回退（单元格 0/1 对应列线 1/2），保持既有行为
        if (!grid.hasLayout()) return cell + 1;
        if (cell == 0) return grid.titleColumn();
        if (cell == 1) return grid.bodyColumn();
        return -1;
    }

    private static boolean looksCorrupt(String line) {
        String text = line.strip();
        if (text.isEmpty()) return false;
        return text.matches(".*[/／]\\s*[*＊].*")
                || text.matches(".*[A-Za-z]{2,}\\s+[A-Za-z]{2,}\\s*[:：].*")
                || text.matches("[（(]?\\d+[)）]\\d*\\s+[A-Za-z]{2,}\\s+[A-Za-z][)）]")
                || text.matches("[\\p{P}\\p{S}\\s]+");
    }

    /** 最少 3 条竖线（2 个内容列）= 序号 + 内容；最多 7 条（6 个内容列）。 */
    private static final int MIN_COLUMNS = 3;
    private static final int MAX_COLUMNS = 7;

    static Grid grid(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<Integer> rows = new ArrayList<>();
        int left = 0, right = width;
        for (int y = 0; y < height; y++) {
            int count = 0, first = width, last = 0;
            for (int x = left; x < right; x++) {
                if (!dark(image, x, y)) continue;
                count++;
                first = Math.min(first, x);
                last = x;
            }
            if (count < (right - left) * (rows.isEmpty() ? 0.60 : 0.78)) continue;
            if (rows.isEmpty()) { left = first; right = last + 10; }
            if (rows.isEmpty() || y - rows.get(rows.size() - 1) > 5) rows.add(y);
        }
        if (rows.size() < 3) return null;
        List<Integer> columns = new ArrayList<>();
        int top = rows.get(0), bottom = rows.get(rows.size() - 1);
        int run = -1;
        int end = Math.min(width - 6, right + 7);
        for (int x = Math.max(5, left - 7); x <= end; x++) {
            int count = 0;
            for (int y = top; y < bottom; y++) {
                for (int offset = -4; offset <= 4; offset++) {
                    if (dark(image, x + offset, y)) { count++; break; }
                }
            }
            if (x < end && count >= (bottom - top) * 0.65) {
                if (run < 0) run = x;
            } else if (run >= 0) {
                columns.add((run + x - 1) / 2);
                run = -1;
            }
        }
        // 需求20：列数不再限定 4 条竖线；超出合理范围（缺列/竖线误检）视为无表格
        return columns.size() >= MIN_COLUMNS && columns.size() <= MAX_COLUMNS
                ? new Grid(rows, columns) : null;
    }

    private static boolean dark(BufferedImage image, int x, int y) {
        int pixel = image.getRGB(x, y);
        return ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) < 615;
    }

    /**
     * 表头判读：返回 {标题列, 正文列}（单元格索引），无法判读时按标准 4 竖线格式回退 {1,2}；
     * 非标准格式且判读失败返回 null（宁可不生成草稿，也不把错误列当成事项内容）。
     */
    static int[] resolveCellColumns(List<String> headers) {
        int cells = headers.size();
        if (cells < 2) return null;
        int title = -1;
        int body = -1;
        for (int i = 0; i < cells; i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).replaceAll("\\s+", "");
            if (title < 0 && (h.contains("项目") || h.contains("事项"))) title = i;
            if (body < 0 && (h.contains("标准") || h.contains("内容") || h.contains("要求"))) body = i;
        }
        if (title < 0 && body > 0) title = body - 1;
        if (body < 0 && title >= 0 && title + 1 < cells) body = title + 1;
        if (title > 0 && body > title && body < cells) return new int[]{title, body};
        // 标准格式（序号|项目|标准，4 条竖线 = 3 个单元格）保持原硬编码列位
        if (cells == 3) return new int[]{1, 2};
        return null;
    }

    static Table blocks(Grid grid, String tsv, String anchor) {
        List<Word> words = new ArrayList<>();
        for (String row : tsv.split("\\R")) {
            String[] fields = row.split("\\t", 12);
            if (fields.length != 12 || !"5".equals(fields[0]) || fields[11].isBlank()) continue;
            int x = Integer.parseInt(fields[6]) + Integer.parseInt(fields[8]) / 2;
            int y = Integer.parseInt(fields[7]) + Integer.parseInt(fields[9]) / 2;
            words.add(new Word(x, y, String.join(":", fields[2], fields[3], fields[4]), fields[11]));
        }
        var rows = grid.rows();
        var columns = grid.columns();
        List<String> headers = new ArrayList<>();
        for (int cell = 0; cell + 1 < columns.size(); cell++) {
            headers.add(cell(words, columns.get(cell), columns.get(cell + 1), rows.get(0), rows.get(1)));
        }
        String mergedHeader = String.join("", headers.subList(1, headers.size()));
        if (!mergedHeader.contains("标准") && !mergedHeader.contains("内容")) return Table.empty(grid);
        int[] cellColumns = resolveCellColumns(headers);
        if (cellColumns == null) return Table.empty(grid);
        Grid laid = grid.withCellColumns(cellColumns[0], cellColumns[1]);
        int titleColumn = cellColumns[0];
        int bodyColumn = cellColumns[1];
        List<KnowledgeDocTextExtractor.SourceBlock> result = new ArrayList<>();
        String before = cell(words, 0, Integer.MAX_VALUE, 0, rows.get(0));
        if (!before.isBlank()) result.add(new KnowledgeDocTextExtractor.SourceBlock(anchor, before, "PDF_PAGE"));
        for (int i = 1; i + 1 < rows.size(); i++) {
            String title = cell(words, columns.get(titleColumn), columns.get(titleColumn + 1), rows.get(i), rows.get(i + 1));
            String body = cell(words, columns.get(bodyColumn), columns.get(bodyColumn + 1), rows.get(i), rows.get(i + 1));
            String rowAnchor = anchor + "表格第" + i + "项";
            if (title.isBlank() && body.isBlank()) continue;   // 空行不当作识别失败，避免整页重跑
            if (title.isBlank() || body.isBlank()) {
                result.add(new KnowledgeDocTextExtractor.SourceBlock(rowAnchor, title + "\n" + body, "PAGE"));
                continue;
            }
            result.add(new KnowledgeDocTextExtractor.SourceBlock(rowAnchor, title + " | " + body, "TABLE_ROW"));
        }
        if (result.stream().noneMatch(block -> "TABLE_ROW".equals(block.kind()))) return Table.empty(laid);
        String after = cell(words, 0, Integer.MAX_VALUE, rows.get(rows.size() - 1), Integer.MAX_VALUE);
        if (!after.isBlank()) result.add(new KnowledgeDocTextExtractor.SourceBlock(anchor, after, "PDF_PAGE"));
        return new Table(result, laid);
    }

    private static String cell(List<Word> words, int left, int right, int top, int bottom) {
        Map<String, StringBuilder> lines = new LinkedHashMap<>();
        for (Word word : words) {
            if (word.x() < left || word.x() >= right || word.y() < top || word.y() >= bottom) continue;
            StringBuilder line = lines.computeIfAbsent(word.line(), key -> new StringBuilder());
            if (!line.isEmpty() && asciiLetterOrDigit(line.charAt(line.length() - 1))
                    && asciiLetterOrDigit(word.text().charAt(0))) line.append(' ');
            line.append(word.text());
        }
        return String.join("\n", lines.values().stream().map(StringBuilder::toString).toList());
    }

    private static boolean asciiLetterOrDigit(char value) {
        return value < 128 && Character.isLetterOrDigit(value);
    }
}
