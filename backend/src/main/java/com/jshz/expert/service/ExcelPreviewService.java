package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class ExcelPreviewService {
    private static final int ROWS_PER_PAGE = 100;
    private static final int COLUMNS_PER_PAGE = 40;

    public record Worksheet(int index, String name) {}
    public record Column(String label, int width) {}
    public record Cell(String text, int rowSpan, int columnSpan) {}
    public record Row(int number, List<Cell> cells) {}
    public record Preview(List<Worksheet> sheets, int sheet, int rowPage, int rowPages,
                          int columnPage, int columnPages, List<Column> columns, List<Row> rows) {}

    public Preview read(Path path, Integer sheetIndex, int rowPage, int columnPage) {
        if (!Files.isRegularFile(path)) throw new BusinessException(404, "文档原件不存在");
        try (var workbook = WorkbookFactory.create(path.toFile(), null, true)) {
            var sheets = IntStream.range(0, workbook.getNumberOfSheets())
                    .filter(i -> !workbook.isSheetHidden(i) && !workbook.isSheetVeryHidden(i))
                    .mapToObj(i -> new Worksheet(i, workbook.getSheetName(i))).toList();
            if (sheets.isEmpty()) throw new BusinessException("工作簿没有可见的工作表");
            int index = sheetIndex == null ? sheets.get(0).index() : sheetIndex;
            if (sheets.stream().noneMatch(sheet -> sheet.index() == index)) {
                throw new BusinessException(404, "工作表不可预览");
            }
            var sheet = workbook.getSheetAt(index);
            var merges = sheet.getMergedRegions();
            int lastRow = sheet.getLastRowNum();
            int lastColumn = -1;
            for (var row : sheet) lastColumn = Math.max(lastColumn, row.getLastCellNum() - 1);
            for (var merge : merges) {
                lastRow = Math.max(lastRow, merge.getLastRow());
                lastColumn = Math.max(lastColumn, merge.getLastColumn());
            }
            int rowPages = pages(lastRow + 1, ROWS_PER_PAGE);
            int columnPages = pages(lastColumn + 1, COLUMNS_PER_PAGE);
            if (rowPage < 0 || rowPage >= rowPages || columnPage < 0 || columnPage >= columnPages) {
                throw new BusinessException("工作表预览页超出范围");
            }
            var rowIndices = IntStream.range(rowPage * ROWS_PER_PAGE, Math.min(lastRow + 1, (rowPage + 1) * ROWS_PER_PAGE))
                    .filter(i -> sheet.getRow(i) == null || !sheet.getRow(i).getZeroHeight()).toArray();
            var columnIndices = IntStream.range(columnPage * COLUMNS_PER_PAGE, Math.min(lastColumn + 1, (columnPage + 1) * COLUMNS_PER_PAGE))
                    .filter(i -> !sheet.isColumnHidden(i)).toArray();
            var columns = IntStream.of(columnIndices).mapToObj(i -> new Column(
                    CellReference.convertNumToColString(i),
                    Math.max(64, Math.min(560, Math.round(sheet.getColumnWidthInPixels(i)))))).toList();

            var formatter = new DataFormatter();
            formatter.setUseCachedValuesForFormulaCells(true);
            Cell[][] mergedCells = new Cell[rowIndices.length][columnIndices.length];
            for (var merge : merges) {
                int top = firstAtLeast(rowIndices, merge.getFirstRow());
                int bottom = firstAtLeast(rowIndices, merge.getLastRow() + 1);
                int left = firstAtLeast(columnIndices, merge.getFirstColumn());
                int right = firstAtLeast(columnIndices, merge.getLastColumn() + 1);
                if (top == bottom || left == right) continue;
                var anchor = sheet.getRow(merge.getFirstRow());
                String value = anchor == null || anchor.getZeroHeight() || sheet.isColumnHidden(merge.getFirstColumn())
                        ? "" : formatter.formatCellValue(anchor.getCell(merge.getFirstColumn()));
                mergedCells[top][left] = new Cell(value, bottom - top, right - left);
                for (int r = top; r < bottom; r++) {
                    for (int c = left; c < right; c++) {
                        if (r != top || c != left) mergedCells[r][c] = new Cell("", 0, 0);
                    }
                }
            }
            List<Row> rows = new ArrayList<>();
            for (int r = 0; r < rowIndices.length; r++) {
                var original = sheet.getRow(rowIndices[r]);
                List<Cell> cells = new ArrayList<>();
                for (int c = 0; c < columnIndices.length; c++) {
                    Cell cell = mergedCells[r][c];
                    if (cell == null) {
                        String value = original == null ? "" : formatter.formatCellValue(original.getCell(columnIndices[c]));
                        cell = new Cell(value, 1, 1);
                    }
                    if (cell.rowSpan() > 0) cells.add(cell);
                }
                rows.add(new Row(rowIndices[r] + 1, cells));
            }
            return new Preview(sheets, index, rowPage, rowPages, columnPage, columnPages, columns, rows);
        } catch (IOException | IllegalArgumentException | org.apache.poi.EncryptedDocumentException e) {
            throw new BusinessException("无法读取 Excel 工作簿，可下载原文件查看");
        }
    }

    private static int pages(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    private static int firstAtLeast(int[] indices, int value) {
        int position = java.util.Arrays.binarySearch(indices, value);
        return position >= 0 ? position : -position - 1;
    }
}
