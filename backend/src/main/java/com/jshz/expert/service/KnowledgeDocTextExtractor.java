package com.jshz.expert.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 知识文档正文抽取（C1 内容检索）。
 * pdf → PDFBox；doc → HWPF(WordExtractor)；docx → XWPF；图片等返回 null（不入内容检索）。
 * 抽取失败返回 null，不阻断上传/更新主流程。
 */
@Component
public class KnowledgeDocTextExtractor {

    public record SourceBlock(String anchor, String text, String kind) {}
    public record StructuredExtraction(List<SourceBlock> blocks, boolean incomplete, String warning) {}

    /** 入库正文长度上限（字符）：大文档截断，兼顾 MEDIUMTEXT 容量与检索意义 */
    private static final int MAX_CHARS = 200_000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            return extract(in, file.getOriginalFilename());
        } catch (Exception e) {
            // 抽取失败不阻断主流程：该文档仅不支持内容检索
            return null;
        }
    }

    /**
     * P2-7：从磁盘路径流式抽取正文，避免 {@code MultipartFile.getBytes()} 在请求线程产生整文件副本。
     * 文件不存在或格式不支持时返回 null，不抛异常。
     */
    public String extract(Path path, String fileName) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            return extract(in, fileName);
        } catch (Exception e) {
            return null;
        }
    }

    private String extract(InputStream in, String fileName) {
        String ext = extensionOf(fileName);
        try {
            String text = switch (ext) {
                case "pdf" -> extractPdf(in);
                case "doc" -> extractDoc(in);
                case "docx" -> extractDocx(in);
                default -> null;
            };
            if (!StringUtils.hasText(text)) {
                return null;
            }
            // Preserve paragraphs and line boundaries for downstream rule parsing.
            String normalized = text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\\n\\n").trim();
            return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) : normalized;
        } catch (Exception e) {
            // 抽取失败不阻断主流程：该文档仅不支持内容检索
            return null;
        }
    }

    /** Extract reviewable source blocks with stable page/paragraph/table anchors. */
    public StructuredExtraction extractStructured(MultipartFile file) {
        if (file == null || file.isEmpty()) return new StructuredExtraction(List.of(), true, "文件为空");
        try { return extractStructured(file.getBytes(), file.getOriginalFilename()); }
        catch (Exception e) { return new StructuredExtraction(List.of(), true, "读取文件失败，请人工核对原文件"); }
    }

    /** Path 重载（合规解析异步链直接读盘，避免调用方持有整文件 byte[]，Owner 2026-11-10）。 */
    public StructuredExtraction extractStructured(Path path, String fileName) {
        try { return extractStructured(Files.readAllBytes(path), fileName); }
        catch (Exception e) { return new StructuredExtraction(List.of(), true, "读取文件失败，请人工核对原文件"); }
    }

    public StructuredExtraction extractStructured(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) return new StructuredExtraction(List.of(), true, "文件为空");
        String ext = extensionOf(fileName);
        List<SourceBlock> blocks = new ArrayList<>();
        try {
            switch (ext) {
                case "pdf" -> {
                    try (PDDocument doc = PDDocument.load(bytes)) {
                        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                            PDFTextStripper stripper = new PDFTextStripper() {
                                private org.apache.pdfbox.text.TextPosition previousLine;

                                @Override
                                protected void writeString(String text, List<org.apache.pdfbox.text.TextPosition> positions)
                                        throws java.io.IOException {
                                    if (!positions.isEmpty()) {
                                        var current = positions.get(0);
                                        float gap = previousLine == null ? 0 : current.getYDirAdj() - previousLine.getYDirAdj();
                                        if (gap > 1) {
                                            float fontSize = Math.max(previousLine.getFontSizeInPt(), current.getFontSizeInPt());
                                            boolean fontChanged = Math.abs(previousLine.getFontSizeInPt() - current.getFontSizeInPt()) >= 3;
                                            if (gap > 3 * fontSize || fontChanged) writeLineSeparator();
                                        }
                                        if (previousLine == null || Math.abs(gap) > 1) previousLine = current;
                                    }
                                    super.writeString(text, positions);
                                }
                            };
                            stripper.setStartPage(i); stripper.setEndPage(i);
                            String page = clean(stripper.getText(doc));
                            if (page.length() < 20 || (page.contains("序号") && page.contains("标准"))) {
                                var raster = new PDFRenderer(doc).renderImageWithDPI(i - 1, 250);
                                var grid = ComplianceOcrTable.grid(raster);
                                if (grid != null) {
                                    String anchor = "PDF第" + i + "页";
                                    var table = ComplianceOcrTable.blocks(grid, LocalTableOcr.recognize(raster), anchor);
                                    if (table.isEmpty() || table.stream().anyMatch(block -> "PAGE".equals(block.kind()))) {
                                        table = ComplianceOcrTable.blocks(grid, ocrPage(raster, true), anchor);
                                    }
                                    if (!table.isEmpty()) {
                                        blocks.addAll(ComplianceOcrTable.refineRows(raster, grid, table));
                                        continue;
                                    }
                                }
                                if (page.length() < 20) {
                                    String ocr = clean(ocrPage(raster, false));
                                    if (ocr.length() >= 20) page = ocr;
                                    // PP-OCRv5 整页行识别竞争（Owner 2026-11-11）：表格定位失败的扫描页原走 Tesseract 整页
                                    // 识别，噪声大（如「国玫汪避ER」「雍训人 基多内」）；PP-OCR 带逐行置信度，滤低分行后与
                                    // Tesseract 按 CJK 字符量择优
                                    String ppocr = ppOcrLines(raster);
                                    if (ppocr != null && cjkCount(ppocr) > cjkCount(page)) page = ppocr;
                                }
                            }
                            blocks.add(new SourceBlock("PDF第" + i + "页", page, page.length() < 20 ? "PAGE" : "PDF_PAGE"));
                        }
                    }
                }
                case "docx" -> {
                    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                        int i = 0;
                        for (IBodyElement e : doc.getBodyElements()) {
                            i++;
                            if (e instanceof XWPFParagraph p) add(blocks, "正文段落" + i, p.getText(), "PARAGRAPH");
                            else if (e instanceof XWPFTable t) {
                                int r = 0;
                                for (XWPFTableRow row : t.getRows()) {
                                    r++;
                                    add(blocks, "表格" + i + "第" + r + "行", row.getTableCells().stream().map(XWPFTableCell::getText).reduce((a,b)->a+" | "+b).orElse(""), "TABLE_ROW");
                                }
                            }
                        }
                    }
                }
                case "pptx" -> {
                    try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
                        int i = 0;
                        for (XSLFSlide slide : show.getSlides()) {
                            i++;
                            StringBuilder text = new StringBuilder();
                            for (XSLFShape shape : slide.getShapes()) if (shape instanceof XSLFTextShape ts && StringUtils.hasText(ts.getText())) text.append(ts.getText()).append('\n');
                            add(blocks, "第" + i + "页", text.toString(), "SLIDE");
                        }
                    }
                }
                case "xlsx", "xls" -> {
                    try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                        for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                            Sheet sheet = wb.getSheetAt(s);
                            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                                Row row = sheet.getRow(r);
                                if (row == null) continue;
                                List<String> cells = new ArrayList<>();
                                row.forEach(c -> cells.add(c.toString()));
                                add(blocks, sheet.getSheetName() + "第" + (r + 1) + "行", String.join(" | ", cells), "TABLE_ROW");
                            }
                        }
                    }
                }
                default -> { return new StructuredExtraction(List.of(), true, "该格式暂不支持结构化提取"); }
            }
            long emptyPages = blocks.stream().filter(b -> "PAGE".equals(b.kind())).count();
            boolean incomplete = blocks.isEmpty() || emptyPages > 0;
            String warning = emptyPages > 0 ? "有 " + emptyPages + " 处扫描页或表格内容未完整提取，请人工核对原文件" : (blocks.isEmpty() ? "未提取到可确认内容" : null);
            return new StructuredExtraction(List.copyOf(blocks), incomplete, warning);
        } catch (Exception e) {
            return new StructuredExtraction(List.copyOf(blocks), true, "提取失败，请人工核对原文件");
        }
    }

    private static String clean(String text) { return text == null ? "" : text.replaceAll("[ \\t]+", " ").trim(); }
    /** OCR 单页/单图（公开供简历解析复用）：chi_sim+eng，45s 超时，临时文件自动清理。 */
    /**
     * PP-OCRv5 整页行识别（Owner 2026-11-11）：调用 LocalTableOcr 的 TSV 输出，
     * 按逐行置信度 ≥60 过滤后拼为文本；运行时未安装/超时/无有效行返回 null（调用方保留 Tesseract 结果）。
     */
    private String ppOcrLines(java.awt.image.BufferedImage raster) {
        try {
            String tsv = LocalTableOcr.recognize(raster);
            if (tsv == null || tsv.isBlank()) return null;
            StringBuilder sb = new StringBuilder();
            for (String line : tsv.split("\\R")) {
                String[] f = line.split("\t");
                if (f.length < 2) continue;
                String text = f[f.length - 1].trim();
                double score;
                try { score = Double.parseDouble(f[f.length - 2]); } catch (Exception e) { continue; }
                if (text.isEmpty() || score < 60) continue;
                sb.append(text).append('\n');
            }
            return sb.toString().isBlank() ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static long cjkCount(String s) {
        return s == null ? 0 : s.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
    }

    public static String ocrPage(java.awt.image.BufferedImage raster, boolean coordinates) {
        Path image = null;
        Path output = null;
        Process process = null;
        try {
            image = Files.createTempFile("knowledge-ocr-", ".png");
            javax.imageio.ImageIO.write(raster, "png", image.toFile());
            output = Files.createTempFile("knowledge-ocr-", ".txt");
            String command = System.getenv().getOrDefault("TESSERACT_CMD", "tesseract");
            Path installed = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Tesseract-OCR", "tesseract.exe");
            if ("tesseract".equals(command) && Files.isRegularFile(installed)) command = installed.toString();
            String configuredData = System.getenv("TESSDATA_DIR");
            Path localData = configuredData == null || configuredData.isBlank()
                    ? Path.of(System.getProperty("user.home"), "AppData", "Local", "Tesseract-OCR", "tessdata")
                    : Path.of(configuredData);
            Path systemData = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Tesseract-OCR", "tessdata");
            Path data = Files.isRegularFile(localData.resolve("chi_sim.traineddata")) ? localData : systemData;
            var arguments = new ArrayList<>(List.of(command, image.toString(), "stdout",
                    "--tessdata-dir", data.toString(), "-l", "chi_sim+eng", "--psm", coordinates ? "11" : "6"));
            if (coordinates) arguments.addAll(List.of("-c", "tessedit_create_tsv=1"));
            process = new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(output.toFile()).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) return "";
            return process.exitValue() == 0 ? Files.readString(output) : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) { return ""; }
        finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (image != null) try { Files.deleteIfExists(image); } catch (Exception ignored) {}
            if (output != null) try { Files.deleteIfExists(output); } catch (Exception ignored) {}
        }
    }
    private static void add(List<SourceBlock> blocks, String anchor, String text, String kind) {
        String value = clean(text); if (!value.isBlank()) blocks.add(new SourceBlock(anchor, value, kind));
    }

    private String extractPdf(InputStream in) throws Exception {
        try (PDDocument doc = PDDocument.load(in)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDoc(InputStream in) throws Exception {
        try (WordExtractor extractor = new WordExtractor(in)) {
            return extractor.getText();
        }
    }

    private String extractDocx(InputStream in) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
