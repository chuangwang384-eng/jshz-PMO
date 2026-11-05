package com.jshz.expert.service;

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
        String ext = extensionOf(file.getOriginalFilename());
        try {
            String text = switch (ext) {
                case "pdf" -> extractPdf(file.getBytes());
                case "doc" -> extractDoc(file.getBytes());
                case "docx" -> extractDocx(file.getBytes());
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

    public StructuredExtraction extractStructured(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) return new StructuredExtraction(List.of(), true, "文件为空");
        String ext = extensionOf(fileName);
        List<SourceBlock> blocks = new ArrayList<>();
        try {
            switch (ext) {
                case "pdf" -> {
                    try (PDDocument doc = PDDocument.load(bytes)) {
                        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                            PDFTextStripper stripper = new PDFTextStripper();
                            stripper.setStartPage(i); stripper.setEndPage(i);
                            String page = clean(stripper.getText(doc));
                            if (page.length() < 20) {
                                String ocr = ocrPage(new PDFRenderer(doc), i - 1);
                                if (ocr.length() >= 20) page = ocr;
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
            long emptyPages = blocks.stream().filter(b -> "PAGE".equals(b.kind()) && b.text().length() < 20).count();
            boolean incomplete = blocks.isEmpty() || emptyPages > 0;
            String warning = emptyPages > 0 ? "有 " + emptyPages + " 页未提取到有效文字，可能是扫描页或图片页，请人工核对原文件" : (blocks.isEmpty() ? "未提取到可确认内容" : null);
            return new StructuredExtraction(List.copyOf(blocks), incomplete, warning);
        } catch (Exception e) {
            return new StructuredExtraction(List.copyOf(blocks), true, "提取失败，请人工核对原文件");
        }
    }

    private static String clean(String text) { return text == null ? "" : text.replaceAll("[ \\t]+", " ").trim(); }
    private static String ocrPage(PDFRenderer renderer, int pageIndex) {
        Path image = null;
        try {
            image = Files.createTempFile("knowledge-ocr-", ".png");
            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(pageIndex, 250), "png", image.toFile());
            String command = System.getenv().getOrDefault("TESSERACT_CMD", "tesseract");
            Path installed = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Tesseract-OCR", "tesseract.exe");
            if ("tesseract".equals(command) && Files.isRegularFile(installed)) command = installed.toString();
            String configuredData = System.getenv("TESSDATA_DIR");
            Path localData = configuredData == null || configuredData.isBlank()
                    ? Path.of(System.getProperty("user.home"), "AppData", "Local", "Tesseract-OCR", "tessdata")
                    : Path.of(configuredData);
            Path systemData = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Tesseract-OCR", "tessdata");
            Path data = Files.isRegularFile(localData.resolve("chi_sim.traineddata")) ? localData : systemData;
            Process p = new ProcessBuilder(command, image.toString(), "stdout", "--tessdata-dir", data.toString(), "-l", "chi_sim+eng", "--psm", "6").redirectErrorStream(true).start();
            if (!p.waitFor(45, TimeUnit.SECONDS)) { p.destroyForcibly(); return ""; }
            String text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return p.exitValue() == 0 ? clean(text) : "";
        } catch (Exception e) { return ""; }
        finally { if (image != null) try { Files.deleteIfExists(image); } catch (Exception ignored) {} }
    }
    private static void add(List<SourceBlock> blocks, String anchor, String text, String kind) {
        String value = clean(text); if (!value.isBlank()) blocks.add(new SourceBlock(anchor, value, kind));
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDoc(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             WordExtractor extractor = new WordExtractor(in)) {
            return extractor.getText();
        }
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
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
