package com.jshz.expert.service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Recognizes ruled tables by matching OCR word coordinates to raster grid cells. */
final class ComplianceOcrTable {
    private record Word(int x, int y, String line, String text) {}
    record Grid(List<Integer> rows, List<Integer> columns) {}

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
        return columns.size() == 4 ? new Grid(rows, columns) : null;
    }

    private static boolean dark(BufferedImage image, int x, int y) {
        int pixel = image.getRGB(x, y);
        return ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) < 615;
    }

    static List<KnowledgeDocTextExtractor.SourceBlock> blocks(Grid grid, String tsv, String anchor) {
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
        String header = cell(words, columns.get(1), columns.get(3), rows.get(0), rows.get(1));
        if (!header.contains("标准") && !header.contains("内容")) return List.of();
        List<KnowledgeDocTextExtractor.SourceBlock> result = new ArrayList<>();
        String before = cell(words, 0, Integer.MAX_VALUE, 0, rows.get(0));
        if (!before.isBlank()) result.add(new KnowledgeDocTextExtractor.SourceBlock(anchor, before, "PDF_PAGE"));
        for (int i = 1; i + 1 < rows.size(); i++) {
            String title = cell(words, columns.get(1), columns.get(2), rows.get(i), rows.get(i + 1));
            String body = cell(words, columns.get(2), columns.get(3), rows.get(i), rows.get(i + 1));
            String rowAnchor = anchor + "表格第" + i + "项";
            if (title.isBlank() || body.isBlank()) {
                result.add(new KnowledgeDocTextExtractor.SourceBlock(rowAnchor, title + "\n" + body, "PAGE"));
                continue;
            }
            result.add(new KnowledgeDocTextExtractor.SourceBlock(rowAnchor, title + " | " + body, "TABLE_ROW"));
        }
        if (result.stream().noneMatch(block -> "TABLE_ROW".equals(block.kind()))) return List.of();
        String after = cell(words, 0, Integer.MAX_VALUE, rows.get(rows.size() - 1), Integer.MAX_VALUE);
        if (!after.isBlank()) result.add(new KnowledgeDocTextExtractor.SourceBlock(anchor, after, "PDF_PAGE"));
        return result;
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
