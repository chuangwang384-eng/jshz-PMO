package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.sl.usermodel.GroupShape;
import org.apache.poi.sl.usermodel.PictureShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.TableShape;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新增专家·简历解析（Owner 2026-11-07 确认口径）：
 * PDF/Word/PPT/图片 → 文本（扫描件回退本机 Tesseract OCR，复用 KnowledgeDocTextExtractor.ocrPage）
 * → 多策略本地字段解析（标签行 + 机构/科室/职称词表 + 上下文推断）→ 候选值；
 * 头像 = 内嵌图片启发式候选（最多 3 张），必须经用户确认；
 * 简历原文不留存，解析用临时文件即用即删。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeParseService {

    private final OllamaService ollamaService;
    private final PhotoMatteService photoMatteService;

    /** 简历单文件上限：单一真源 application.yml expert.upload.resume-max-bytes。 */
    @Value("${expert.upload.resume-max-bytes}")
    private long resumeMaxBytes = 30L * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "doc", "docx", "ppt", "pptx", "jpg", "jpeg", "png");

    /**
     * 职称词表（Owner 2026-11-19 定稿）：数组顺序即匹配优先级——同一行命中多个职称时
     * 取顺序最前者（如"主任医师、教授"填主任医师）；子串命中被更长词覆盖时让位
     * （如"副主任中医师"取整体而非子串"医师"）。
     */
    private static final List<String> TITLE_WORDS = List.of(
            "主任医师", "副主任医师", "主治医师", "医师", "医士",
            "主任药师", "副主任药师", "主管药师", "药师", "药士",
            "主任护师", "副主任护师", "主管护师", "护师", "护士",
            "主任技师", "副主任技师", "主管技师", "技师", "技士",
            "主任中医师", "副主任中医师", "主治中医师", "中医师", "中医士",
            "教授", "副教授", "讲师", "助教",
            "研究员", "副研究员", "助理研究员", "研究实习员");

    /** 模糊别名（Owner 2026-11-19："主任"→主任医师等）。仅在无精确职称词命中时兜底。 */
    private static final List<String[]> TITLE_ALIASES = List.of(
            new String[]{"副主任", "副主任医师"},
            new String[]{"主任", "主任医师"},
            new String[]{"主治", "主治医师"});

    /** 机构后缀词表。 */
    private static final List<String> ORG_SUFFIXES = List.of(
            "医院", "研究院", "研究所", "大学", "学院", "中心", "公司", "卫生院", "疾控中心", "医学会");

    /** 科室特征词。 */
    private static final List<String> DEPT_SUFFIXES = List.of("科", "病房", "研究室", "办公室", "中心", "部", "组");

    /** 学术兼职特征词（2026-11-19 王俊生案例）：含这些词的行是学会/委员会任职，不作为单位候选。 */
    private static final List<String> MEMBERSHIP_WORDS = List.of(
            "委员", "理事", "常委", "主委", "会员", "秘书", "组组长");

    /** 姓名启发式排除词（首行含这些词时不视为姓名）。 */
    private static final List<String> NAME_EXCLUDE_WORDS = List.of(
            "简历", "个人", "求职", "专家", "简介", "resume", "curriculum", "个人简历", "某某", "基本情况", "学术任职", "学术成就", "讨论嘉宾");

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINESE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5]{2,4}$|^(?=.{3,20}$)[\\u4e00-\\u9fa5]+(?:·[\\u4e00-\\u9fa5]+)+$");

    /** 证件照候选：base64 + mime + 是否已抠透明背景（rembg 成功=true，回退白底 JPG=false） */
    public record PhotoCandidate(String base64, String mime, boolean transparent) {}

    /** 解析结果：fields 仅含命中的字段（键与专家表单中文名一致）。 */
    public record ResumeParseResult(Map<String, String> fields, List<PhotoCandidate> photoCandidates,
                                    int hitCount, String message) {}

    /** 解析入口：按扩展名分发；非法格式/超限直接 400。 */
    public ResumeParseResult parse(String fileName, byte[] bytes) {
        return parse(fileName, bytes, true);
    }

    public ResumeParseResult parse(String fileName, byte[] bytes, boolean allowFilenameFallback) {
        String ext = com.jshz.expert.common.FileNames.extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "不支持的简历格式（支持 pdf/doc/docx/ppt/pptx/jpg/png）");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(400, "简历文件为空");
        }
        if (bytes.length > resumeMaxBytes) {
            throw new BusinessException(400, "简历文件超过 " + (resumeMaxBytes / 1024 / 1024) + "MB 限制");
        }
        try {
            return switch (ext) {
                case "pdf" -> parsePdf(fileName, bytes, allowFilenameFallback);
                case "doc" -> parseDoc(fileName, bytes, allowFilenameFallback);
                case "docx" -> parseDocx(fileName, bytes, allowFilenameFallback);
                case "ppt" -> FileMagic.valueOf(bytes) == FileMagic.OOXML ? parsePptx(fileName, bytes, allowFilenameFallback) : parsePpt(fileName, bytes, allowFilenameFallback);
                case "pptx" -> parsePptx(fileName, bytes, allowFilenameFallback);
                case "jpg", "jpeg", "png" -> parseImage(fileName, bytes, allowFilenameFallback);
                default -> throw new BusinessException(400, "不支持的简历格式");
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("简历解析失败 file={} err={}", fileName, e.toString(), e);
            return new ResumeParseResult(Map.of(), List.of(), 0,
                    "解析失败（" + e.getClass().getSimpleName() + "），请手动填写");
        }
    }

    private boolean isCandidateName(String value) {
        return isNamedPerson(value) && DEPT_SUFFIXES.stream().noneMatch(value::endsWith);
    }

    private boolean isNamedPerson(String value) {
        return value != null && CHINESE_NAME.matcher(value).matches()
                && NAME_EXCLUDE_WORDS.stream().noneMatch(value::contains)
                && ORG_SUFFIXES.stream().noneMatch(value::endsWith)
                && !value.matches(".*(博士|硕士|学士|医师|教授|主任)$");
    }

    /**
     * 科室候选收集（2026-11-19 Owner：科室优先找文件内的"科"字）：对每个"科"字向前截取
     * 连续汉字段（跳过单位机构名，≤10 字）作为候选，供 LLM 判定前后组合语义。
     * 任职行/教育行不参与。
     */
    List<String> collectDeptCandidates(String text) {
        List<String> cands = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String compact = line.replaceAll("[ \\t　]", "");
            if (compact.isEmpty() || compact.length() > 40) continue;
            if (compact.contains("简历") || compact.contains("毕业")) continue;
            if (MEMBERSHIP_WORDS.stream().anyMatch(compact::contains)) continue;
            for (int i = compact.indexOf('科'); i >= 0; i = compact.indexOf('科', i + 1)) {
                int s = 0;
                // 机构名之后截取（"许昌中心医院|乳腺甲状腺外科"），防单位名混入科室候选
                for (String suffix : ORG_SUFFIXES) {
                    int e = compact.lastIndexOf(suffix, i - 1);
                    if (e >= 0 && e + suffix.length() < i) s = Math.max(s, e + suffix.length());
                }
                if (i + 1 - s > 10) s = i + 1 - 10;
                String seg = compact.substring(s, i + 1);
                if (seg.length() >= 2 && !cands.contains(seg)) cands.add(seg);
            }
        }
        return cands;
    }

    /**
     * 机构内嵌名检测（2026-11-19 李晓龙案例，Owner：姓名疑似城市/学校/单位字段先不填入）：
     * 候选姓名若紧邻出现在原文机构名开头（候选后 ≤2 字即接机构后缀，如"许昌|中心医院"、
     * "河南|科技大学"），判定为机构名片段而非人名。真正的姓名行（"李晓龙 教授"）不受影响。
     * 返回 true 表示该候选应作废，交由文件名兜底重新取名。
     */
    /**
     * 任职行取值检测（2026-11-19 王俊生案例）：候选值所在行含学术兼职特征词
     * （委员/理事/主委等）时，该值是学会/委员会任职机构而非任职单位/科室。
     */
    boolean membershipLineValue(String value, String text) {
        if (value == null || value.isBlank()) return false;
        String v = value.replaceAll("\\s+", "");
        for (String line : text.split("\\R")) {
            String compact = line.replaceAll("[ \\t　]", "");
            if (compact.isEmpty() || !compact.contains(v)) continue;
            if (MEMBERSHIP_WORDS.stream().anyMatch(compact::contains)) return true;
        }
        return false;
    }

    boolean isOrgEmbeddedName(String name, String text) {
        if (name == null || name.isBlank()) return false;
        String compactName = name.replaceAll("\\s+", "");
        for (String line : text.split("\\R")) {
            String compact = line.replaceAll("[ \\t　]", "");
            if (!compact.contains(compactName)) continue;
            int nameEnd = compact.indexOf(compactName) + compactName.length();
            for (String suffix : ORG_SUFFIXES) {
                int idx = compact.indexOf(suffix, nameEnd);
                if (idx >= 0 && idx - nameEnd <= 2) return true;
            }
        }
        return false;
    }

    /** 简历类后缀词（可带"最新/新版/简洁版"等版式变体与纯数字版次，如"简历2""简介2024""简介简洁版"）。 */
    private static final String RESUME_SUFFIX_PATTERN = "(?:个人简介|专家简介|的简历|简历|简介)+(?:最新版|新版|更新版|最新|新|简洁版|精简版|简版|完整版)?\\d*";

    String nameFromFileName(String fileName) {
        return nameFromFileName(fileName, List.of());
    }

    private String nameFromFileName(String fileName, List<String> standaloneLines) {
        if (fileName == null || fileName.isBlank()) return null;
        String base = fileName.replaceFirst("(?i)\\.(pdf|docx?|pptx?|jpe?g|png)$", "").trim();
        // 去掉常见后缀词后再分词
        base = base.replaceAll(RESUME_SUFFIX_PATTERN + "$", "").trim();
        String[] tokens = base.split("[\\s\\-_.（）()、，,]+");
        for (String token : tokens) {
            String t = token.replaceFirst("^(?:副本)+", "")
                    .replaceFirst(RESUME_SUFFIX_PATTERN + "$", "");
            if (isCandidateName(t) || (isNamedPerson(t) && standaloneLines.contains(t))) return t;
        }
        for (String token : tokens) {
            for (String suffix : ORG_SUFFIXES) {
                int start = token.lastIndexOf(suffix);
                if (start < 2) continue;
                String tail = token.substring(start + suffix.length());
                if (isCandidateName(tail)) return tail;
            }
        }
        return null;
    }

    /**
     * 文件名单位候选（2026-11-19 由 resolveUnit 改为纯函数，优先级变为"组合行>文件名>散行扫描"）：
     * 从文件名分词中取以机构后缀结尾的候选，如"副本李宏简介-湖北省肿瘤医院 1.pptx"→"湖北省肿瘤医院"。
     */
    String unitFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String base = fileName.replaceFirst("(?i)\\.(pdf|docx?|pptx?|jpe?g|png)$", "").trim();
        for (String token : base.split("[\\s\\-_.、，,]+")) {
            String candidate = token.replaceFirst("^(?:副本|[0-9]+)+", "");
            if (candidate.length() < 4 || candidate.length() > 40
                    || candidate.contains("某") || NAME_EXCLUDE_WORDS.stream().anyMatch(candidate::contains)
                    || ORG_SUFFIXES.stream().noneMatch(candidate::endsWith)) continue;
            return candidate;
        }
        return null;
    }

    // ==================== 格式分发 ====================

    private ResumeParseResult parsePdf(String fileName, byte[] bytes, boolean allowFilenameFallback) throws IOException {
        List<BufferedImage> images;
        String text;
        try (PDDocument doc = PDDocument.load(bytes)) {
            images = extractPdfImages(doc);
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            // 扫描件回退：每页平均文本过少时栅格化前 3 页 OCR
            if (text.trim().length() < Math.max(20L, doc.getNumberOfPages() * 20L)) {
                PDFRenderer renderer = new PDFRenderer(doc);
                int pages = Math.min(3, doc.getNumberOfPages());
                StringBuilder ocr = new StringBuilder();
                for (int i = 0; i < pages; i++) {
                    BufferedImage raster = renderer.renderImageWithDPI(i, 150);
                    ocr.append(KnowledgeDocTextExtractor.ocrPage(raster, false)).append('\n');
                }
                if (ocr.length() > text.trim().length()) {
                    text = ocr.toString();
                }
            }
        }
        return buildResult(fileName, text, images, allowFilenameFallback);
    }

    /**
     * 老版 .doc：文本 HWPF 抽取；文档内所有图片（含证件照）通过 PicturesTable 抽出作为头像候选
     * （Owner 2026-11-07：doc 内所有图片都可能是头像）。
     */
    private ResumeParseResult parseDoc(String fileName, byte[] bytes, boolean allowFilenameFallback) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        String text;
        org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes));
        try {
            text = new WordExtractor(doc).getText();
            for (org.apache.poi.hwpf.usermodel.Picture pic : doc.getPicturesTable().getAllPictures()) {
                collectImage(images, pic.getContent());
            }
        } finally {
            try { doc.close(); } catch (Exception ignored) {}
        }
        return buildResult(fileName, text, images, allowFilenameFallback);
    }

    private ResumeParseResult parseDocx(String fileName, byte[] bytes, boolean allowFilenameFallback) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text = extractor.getText();
            // 文本框正文抽取（2026-11-18 杨晨原件回归）：设计版式把姓名/单位/职称放进
            // w:txbxContent，XWPFWordExtractor 只读顶层段落，导致除姓名外全部丢失。
            String textBox = extractDocxTextBoxText(doc);
            if (!textBox.isBlank()) {
                text = textBox + (text == null || text.isBlank() ? "" : "\n" + text);
            }
            doc.getAllPictures().forEach(pic -> collectImage(images, pic.getData()));
        }
        return buildResult(fileName, text, images, allowFilenameFallback);
    }

    /** 抽取 docx 全部文本框段落（w:txbxContent），按段落拼行；无文本框返回空串。 */
    private String extractDocxTextBoxText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        String ns = "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' ";
        for (org.apache.xmlbeans.XmlObject para : doc.getDocument().getBody().selectPath(ns + ".//w:txbxContent//w:p")) {
            StringBuilder line = new StringBuilder();
            try (var cursor = para.newCursor()) {
                cursor.selectPath(ns + ".//w:t");
                while (cursor.toNextSelection()) {
                    String value = cursor.getTextValue();
                    if (value != null) line.append(value);
                }
            }
            String trimmed = line.toString().replaceAll("[ \\t]+", " ").trim();
            if (!trimmed.isEmpty()) sb.append(trimmed).append('\n');
        }
        return sb.toString();
    }

    private record SlideContent(String text, List<BufferedImage> images) {}

    private ResumeParseResult parsePpt(String fileName, byte[] bytes, boolean allowFilenameFallback) throws IOException {
        SlideContent content;
        try (HSLFSlideShow ppt = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
            content = extractSlideContent(ppt);
        }
        return buildResult(fileName, content.text(), content.images(), allowFilenameFallback);
    }

    private ResumeParseResult parsePptx(String fileName, byte[] bytes, boolean allowFilenameFallback) throws IOException, InvalidFormatException {
        // 走 ZIP 中央目录读取条目大小，避免流式读取带数据描述符的条目时误报解压比。
        // 临时文件使用系统分配的路径，POI 以只读方式打开并保留 ZIP 安全检查。
        Path temporary = Files.createTempFile("resume-slides-", ".pptx");
        SlideContent content;
        try {
            Files.write(temporary, bytes);
            try (OPCPackage pkg = OPCPackage.open(temporary.toFile(), PackageAccess.READ);
                 XMLSlideShow ppt = new XMLSlideShow(pkg)) {
                content = extractSlideContent(ppt);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return buildResult(fileName, content.text(), content.images(), allowFilenameFallback);
    }

    private SlideContent extractSlideContent(SlideShow<?, ?> ppt) {
        List<BufferedImage> images = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean unreadableShapes = false;
        BufferedImage imagePage = null;
        var pageSize = ppt.getPageSize();
        for (var slide : ppt.getSlides()) {
            List<? extends Shape<?, ?>> shapes;
            try {
                shapes = slide.getShapes();
            } catch (IndexOutOfBoundsException e) {
                if (!(slide instanceof XSLFSlide xslf)) throw e;
                // OOXML 的形状 ID 是无符号整数，POI 构造形状树时可能将它转成负数。
                collectOoxmlParagraphs(xslf, text);
                unreadableShapes = true;
                continue;
            }
            for (var shape : shapes) {
                int imageCount = images.size();
                collectSlideShape(shape, text, images);
                if (imagePage == null && images.size() > imageCount && shape instanceof PictureShape<?, ?> picture) {
                    var anchor = picture.getAnchor();
                    // 区分整页简历截图与普通证件照、标识。只用幻灯片上实际出现的图片。
                    if (anchor != null && anchor.getWidth() >= pageSize.getWidth() * 0.6
                            && anchor.getHeight() >= pageSize.getHeight() * 0.6) {
                        imagePage = images.get(imageCount);
                    }
                }
            }
        }
        // 包内媒体也包含母版和未引用的装饰图，只有幻灯片图片不足时才使用。
        if (unreadableShapes || images.stream().noneMatch(this::isAvatarLike)) {
            ppt.getPictureData().forEach(picture -> collectImage(images, picture.getData()));
        }
        String extractedText = text.toString();
        if (extractedText.isBlank() && imagePage != null) {
            extractedText = recognizeResumeImage(enlargeSlideImageForOcr(imagePage));
        }
        return new SlideContent(extractedText, images);
    }

    private void collectOoxmlParagraphs(XSLFSlide slide, StringBuilder text) {
        String drawingNs = "declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main'; ";
        for (var paragraph : slide.getXmlObject().getCSld().getSpTree().selectPath(drawingNs + ".//a:p")) {
            for (var run : paragraph.selectPath(drawingNs + ".//a:t | .//a:br")) {
                try (var cursor = run.newCursor()) {
                    text.append("br".equals(cursor.getName().getLocalPart()) ? "\n" : cursor.getTextValue());
                }
            }
            text.append('\n');
        }
    }

    /** 递归收集幻灯片图片与文本，表格文本按单元格拼接。 */
    private void collectSlideShape(Shape<?, ?> shape, StringBuilder text, List<BufferedImage> images) {
        if (shape instanceof PictureShape<?, ?> picture) {
            var data = picture.getPictureData();
            if (data != null) collectImage(images, data.getData());
        } else if (shape instanceof TableShape<?, ?> table) {
            for (int row = 0; row < table.getNumberOfRows(); row++) {
                for (int column = 0; column < table.getNumberOfColumns(); column++) {
                    var cell = table.getCell(row, column);
                    String value = cell == null ? null : cell.getText();
                    if (value != null && !value.isBlank()) text.append(value.trim()).append('\n');
                }
            }
        } else if (shape instanceof GroupShape<?, ?> group) {
            for (var child : group) {
                collectSlideShape(child, text, images);
            }
        } else if (shape instanceof TextShape<?, ?> ts) {
            text.append(ts.getText()).append('\n');
        }
    }

    private ResumeParseResult parseImage(String fileName, byte[] bytes, boolean allowFilenameFallback) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new BusinessException(400, "无法读取图片内容");
        }
        return buildResult(fileName, recognizeResumeImage(image), List.of(image), allowFilenameFallback);
    }

    private BufferedImage enlargeSlideImageForOcr(BufferedImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        // 满幅截图缩小后容易丢失密集汉字的笔画，放大时限制最长边，控制 OCR 开销。
        double scale = Math.min(2.0, 2200.0 / longest);
        if (scale <= 1.0) return image;
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        BufferedImage enlarged = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = enlarged.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return enlarged;
    }

    private String recognizeResumeImage(BufferedImage image) {
        String text = KnowledgeDocTextExtractor.ppOcrLines(image);
        if (text == null && !Thread.currentThread().isInterrupted()) {
            text = KnowledgeDocTextExtractor.ocrPage(image, false);
        }
        return text;
    }

    // ==================== 字段解析 ====================

    ResumeParseResult buildResult(String fileName, String text, List<BufferedImage> images, boolean allowFilenameFallback) {
        // photoMatteService 判空保留：隔离测试以 new ResumeParseService(null, null) 构造
        // （2026-11-23 复审曾误判为死代码，移除致 4 个解析测试失败，已恢复）
        var photos = photoMatteService == null || images == null || images.isEmpty() ? null
                : photoMatteService.candidates(() -> buildPhotoCandidates(images, true));
        Map<String, String> fields = new LinkedHashMap<>();
        String message = "";
        if (text == null || text.isBlank()) {
            message = "未识别到有效文本（可能为扫描件或图片质量不佳），请手动填写";
        } else {
            // 策略一：多策略本地启发式打底；策略二：本地 LLM 语义抽取（未配置 Ollama 时自动跳过）
            // 内容阶段：组合行（单位+科室+职称）> 标签行 > 散行机构扫描；LLM 只补缺失字段
            String filenameName = allowFilenameFallback
                    ? nameFromFileName(fileName, text.lines().map(line -> line.replaceAll("\\s+", "")).toList())
                    : null;
            if (filenameName != null && !isCandidateName(filenameName)) fields.put("姓名", filenameName);
            String scannedUnit = extractFields(text, fields);
            if (!fields.containsKey("单位") && scannedUnit != null) {
                fields.put("单位", scannedUnit);
            }
            Map<String, String> semantic = ollamaService.extractResumeFields(text);
            if (semantic != null && !semantic.isEmpty()) {
                String normalizedText = text.replaceAll("\\s+", "");
                semantic.forEach((key, value) -> {
                    // 单位先净化（剥离混入的姓名），再走机构后缀/原文包含校验
                    String val = "单位".equals(key) ? sanitizeUnit(value, fields.get("姓名")) : value;
                    if ("单位".equals(key) && (fields.containsKey(key) || val == null
                            || ORG_SUFFIXES.stream().noneMatch(val.strip()::endsWith)
                            || membershipLineValue(val, text)
                            || !normalizedText.contains(val.replaceAll("\\s+", "")))) return;
                    // 2026-11-18 联系方式/职称/科室防幻觉（Ollama 曾臆造占位手机号 13800001234）：
                    // 三者必须能在原文（去空白）中找到才采纳；联系方式另有格式约束
                    if ("联系方式".equals(key)) {
                        if (val == null || val.isBlank()) return;
                        if (val.contains("@")) {
                            if (!EMAIL.matcher(val).matches()
                                    || !normalizedText.contains(val.replaceAll("\\s+", ""))) return;
                        } else {
                            String digits = val.replaceAll("\\D", "");
                            if (digits.startsWith("86") && digits.length() > 11) digits = digits.substring(2);
                            String textDigits = normalizedText.replaceAll("\\D", "");
                            if (digits.length() < 7 || !textDigits.contains(digits)) return;
                        }
                    }
                    if ("职称".equals(key)) {
                        // 职称归一（2026-11-19）：LLM 候选必须能归一到词表（含模糊别名）且原文可寻；
                        // 本地已解析职称（顺序优先结果）不得被 LLM 覆盖（曾出现本地主治医师被 LLM 教授覆盖）
                        val = canonicalTitle(val);
                        if (val == null || fields.containsKey("职称") || !normalizedText.contains(val)) return;
                    }
                    if ("科室".equals(key) && (val == null || fields.containsKey("科室")
                            || DEPT_SUFFIXES.stream().noneMatch(val.strip()::endsWith)
                            || membershipLineValue(val, text)
                            || !normalizedText.contains(val.replaceAll("\\s+", "")))) return;
                    if (!"姓名".equals(key) || (!isNamedPerson(fields.get("姓名"))
                            && isCandidateName(val) && normalizedText.contains(val))) {
                        fields.put(key, val);
                    }
                });
                if (!fields.isEmpty()) message = "已结合本地语义模型解析";
            }
            // 无效姓名不算已归入结构化字段，原文仍应进入简介。
            if (!isNamedPerson(fields.get("姓名"))) fields.remove("姓名");
            // 2026-11-19 李晓龙案例：Ollama 曾把"许昌中心医院"里的"许昌"当姓名——
            // 表面像人名实为机构/城市名前缀的候选先不填入
            if (isOrgEmbeddedName(fields.get("姓名"), text)) fields.remove("姓名");

            // 文件名第一优先（Owner 2026-11-19）：姓名/单位取文件名候选，但必须与原文比对
            // 通过才填入（比对含省/市宽松变体）；比对不上时保留上面的内容解析结果。
            String compactText = text.replaceAll("\\s+", "");
            String fnName = filenameName;
            if (fnName != null && (fields.get("姓名") == null
                    || compactText.contains(fnName)
                    || compactText.contains(fnName.replace("省", "").replace("市", "")))) {
                fields.put("姓名", fnName);
            }
            String fnUnit = unitFromFileName(fileName);
            // 单位文件名绝对第一优先（2026-11-19 王俊生案例：内容任职行机构"安阳市医学会"
            // 不得压过文件名"安阳市肿瘤医院"；医院名常在 LOGO 图片里，文本无佐证属正常）
            if (fnUnit != null) {
                String sanitized = sanitizeUnit(fnUnit, fields.get("姓名"));
                if (sanitized != null) fields.put("单位", sanitized);
            }
            // 姓名落定后二次剥离单位里的姓名前缀（承接原 sanitizeUnitAgainstName 职责，
            // 如内容单位"卫利民河南科技大学第一附属医院"；剩余 <6 字不剥防"华山|医院"误伤）
            String finalName = fields.get("姓名");
            String finalUnit = fields.get("单位");
            if (finalName != null && finalUnit != null && finalUnit.startsWith(finalName)
                    && finalUnit.length() - finalName.length() >= 6) {
                String stripped = sanitizeUnit(finalUnit, finalName);
                if (stripped != null && !stripped.equals(finalUnit)) fields.put("单位", stripped);
            }
            // 科室 LLM 语义辅助（Owner 2026-11-19）：本地未解析出科室时，收集文中含"科"字的
            // 候选段，交本地 LLM 判定前后组合语义是否为科室（任职委员会/学会不算）。
            if (fields.get("姓名") != null && fields.get("姓名").equals(fields.get("科室"))) {
                fields.remove("科室");
            }
            if (!fields.containsKey("科室")) {
                List<String> deptCandidates = collectDeptCandidates(text);
                deptCandidates.remove(fields.get("姓名"));
                if (!deptCandidates.isEmpty()) {
                    String picked = ollamaService.pickResumeDepartment(deptCandidates);
                    if (picked != null && deptCandidates.contains(picked)) fields.put("科室", picked);
                }
            }
            if (fields.isEmpty()) {
                message = "已识别文本但未命中可填充字段，请手动填写";
            }
        }
        // 未归入五个字段的简历文本统一收入简介（保留换行；仅在简介为空时填充）
        if (!fields.containsKey("简介")) {
            String profile = collectProfileLines(text, fields);
            if (!profile.isEmpty()) {
                fields.put("简介", profile);
            }
        }
        List<PhotoCandidate> photoCandidates = photos == null ? List.of() : photos.join();
        if (photoCandidates.isEmpty()) photoCandidates = buildPhotoCandidates(images, false);
        return new ResumeParseResult(fields, photoCandidates, fields.size(), message);
    }

    private List<PhotoCandidate> buildPhotoCandidates(List<BufferedImage> images, boolean matte) {
        // 头像候选：先取"头像特征"过滤结果；若全部被过滤，回退为文档内所有图片
        //（Owner 2026-11-07：doc 等格式内所有图片都可能是头像）
        List<PhotoCandidate> photoCandidates = new ArrayList<>();
        List<BufferedImage> safeImages = images == null ? List.of() : images;
        List<BufferedImage> pool = safeImages.stream().filter(this::isAvatarLike)
                .sorted(java.util.Comparator.comparing(img -> img.getWidth() > img.getHeight()))
                .toList();
        if (pool.isEmpty()) pool = safeImages;
        for (BufferedImage img : pool) {
            if (photoCandidates.size() >= 3) break;
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
            try {
                // Owner 2026-11-11：先转无损 PNG 保留原始像素 → rembg 抠图 → 透明证件照；
                // 抠图失败（运行时未装/超时）回退白底 JPG（原行为）
                byte[] matted = matte ? photoMatteService.removeBackground(toPngBytes(resizePortraitInput(img))) : null;
                if (matted != null) {
                    // 抠图后按最长边 600px 缩放（保持透明通道；避免文档大图 base64 过大）
                    byte[] scaled = downscalePng(matted, 600);
                    photoCandidates.add(new PhotoCandidate(
                            Base64.getEncoder().encodeToString(scaled), "image/png", true));
                } else {
                    String candidate = toBase64Jpeg(img);
                    if (!candidate.isEmpty()) {
                        photoCandidates.add(new PhotoCandidate(candidate, "image/jpeg", false));
                    }
                }
            } catch (Exception ignored) {
                // 单张候选失败不影响其余
            }
        }
        return photoCandidates;
    }

    /** 头像特征：最短边 80px、宽高比 0.5~1.8。大图在推理前缩放。 */
    private boolean isAvatarLike(BufferedImage img) {
        if (img == null) return false;
        int w = img.getWidth(), h = img.getHeight();
        if (w < 80 || h < 80) return false;
        double ratio = (double) w / h;
        return ratio >= 0.5 && ratio <= 1.8;
    }

    private BufferedImage resizePortraitInput(BufferedImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= 1200) return image;
        double scale = 1200.0 / longest;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    /** 解码图片字节并收集（解码失败或 0 尺寸异常图跳过）。 */
    private void collectImage(List<BufferedImage> images, byte[] data) {
        if (data == null || data.length == 0) return;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) images.add(img);
        } catch (Exception ignored) {
        }
    }

    /**
     * 收集未归入 姓名/单位/科室/职称/联系方式 的文本行进简介，保留原始顺序与换行。
     * 排除：已抽取字段的取值行、这些字段的标签行、空行。超长截断到 2000 字符。
     */
    String collectProfileLines(String text, Map<String, String> fields) {
        List<String> leftover = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (String v : List.of("姓名", "单位", "科室", "职称", "联系方式")) {
            String value = fields.get(v);
            if (value != null && !value.isBlank()) values.add(value.trim());
        }
        for (String line : text.split("\\R")) {
            String v = line == null ? "" : line.replaceAll("[ \\t]+", " ").trim();
            if (v.isEmpty() || v.matches("\\d{1,3}") || v.matches("-?\\d+页?")
                    || v.matches("第\\d+页.*共\\d+页")) continue;
            if (values.contains(v)) continue;
            if (CONSUMED_LABELS.stream().anyMatch(v::contains)) continue;
            if (!leftover.contains(v)) leftover.add(v);
        }
        String joined = String.join("\n", leftover);
        return joined.length() > 2000 ? joined.substring(0, 2000) : joined;
    }

    /** 已被结构化字段消费的标签关键词（命中即视为该行信息已归位）。 */
    private static final List<String> CONSUMED_LABELS = List.of(
            "姓名", "单位", "科室", "职称", "联系方式", "联系电话", "电话", "手机", "邮箱", "Email", "E-mail");

    /**
     * 多策略字段解析：标签行优先，词表/上下文推断次之；首个可信命中即停。
     * 返回散行机构扫描得到的单位候选（弱置信，由调用方排在文件名之后兜底）；
     * 组合行直接命中单位时返回 null（单位已写入 fields）。
     */
    String extractFields(String text, Map<String, String> fields) {
        String[] lines = text.split("\\R");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String v = line == null ? "" : line.replaceAll("[ \\t]+", " ").trim();
            if (v.isEmpty()) continue;
            // open-resume 式段落清洗：丢弃页码/页眉页脚噪声（对 OCR 文本尤其重要）
            if (v.matches("\\d{1,3}") || v.matches("-?\\d+页?") || v.matches("第\\d+页.*共\\d+页")) continue;
            cleaned.add(v);
        }
        String full = text.replaceAll("[ \\t]", "");

        String name = extractByLabel(cleaned, "姓名", "Name", 2, 20);
        if (!isNamedPerson(name)) {
            name = fields.get("姓名");
        }
        if (name == null) {
            for (int i = 1; i < cleaned.size(); i++) {
                if (!cleaned.get(i).matches("个人简介|个人简历")) continue;
                String preceding = cleaned.get(i - 1).replaceAll("\\s+", "");
                if (isCandidateName(preceding)) {
                    name = preceding;
                    break;
                }
            }
        }
        if (name == null) {
            // 无标签简历：前 3 行内寻找 2~4 汉字人名（首行常为"个人简历"类标题）
            for (int i = 0; i < Math.min(3, cleaned.size()) && name == null; i++) {
                String line = cleaned.get(i);
                if (isCandidateName(line)) {
                    name = line;
                }
            }
        }
        if (name != null) fields.put("姓名", name);

        // 组合行识别（2026-11-19 李宏案例，Owner 要求单位最高优先级）：
        // 一行同时含"医院单位+科室+医师职称"（如"湖北省肿瘤医院微创介入科副主任医师"）时，
        // 该行是任职陈述，单位置信度远高于散行机构扫描——"日本上尾中央综合医院访问学者"
        // 这类经历行不得压过它。PPT 常把该句拆成两个文本行（"…医院微创"+"介入科副主任医师"），
        // 故单行不命中时再尝试相邻 2/3 行拼接；命中时单位/科室/职称一并落定。
        String comboUnit = null;
        List<String> comboCandidates = new ArrayList<>();
        for (String line : cleaned) comboCandidates.add(line.replace(" ", ""));
        for (int i = 0; i + 1 < cleaned.size(); i++) {
            comboCandidates.add(comboCandidates.get(i) + comboCandidates.get(i + 1));
        }
        for (int i = 0; i + 2 < cleaned.size(); i++) {
            comboCandidates.add(comboCandidates.get(i) + comboCandidates.get(i + 1) + comboCandidates.get(i + 2));
        }
        List<String> standaloneClauses = cleaned.stream()
                .flatMap(line -> java.util.Arrays.stream(line.split("[，,。；;]")))
                .map(clause -> clause.replaceAll("[ \\t　]", ""))
                .toList();
        comboCandidates.addAll(0, standaloneClauses);
        for (String compact : comboCandidates) {
            if (compact.length() > 40 || compact.length() < 8 || compact.contains("简历")) continue;
            if (compact.contains("毕业") || compact.matches("\\d{4}.*")) continue;
            String[] combo = matchUnitDeptTitle(compact);
            if (combo == null) continue;
            comboUnit = combo[0];
            // 组合行单位可能带姓名前缀（"李向东，汝州市第一人民医院…科副主任医师"），净化后落定
            String sanitizedCombo = sanitizeUnit(comboUnit, name);
            fields.put("单位", sanitizedCombo != null ? sanitizedCombo : comboUnit);
            if (combo[1] != null && !fields.containsKey("科室")) fields.put("科室", combo[1]);
            if (combo[2] != null && !fields.containsKey("职称")) fields.put("职称", combo[2]);
            break;
        }

        // 职称可能在前面的独立分句，医院与科室仍可作为同一任职信息提取。
        String affiliatedUnit = null;
        if (comboUnit == null) {
            for (String clause : standaloneClauses) {
                if (clause.contains("毕业") || MEMBERSHIP_WORDS.stream().anyMatch(clause::contains)) continue;
                Matcher affiliation = HOSPITAL_DEPARTMENT.matcher(clause);
                if (!affiliation.matches()) continue;
                affiliatedUnit = affiliation.group(1);
                fields.put("科室", affiliation.group(2));
                break;
            }
        }

        String unit = extractByLabel(cleaned, "单位", "Unit", 2, 40);
        if (unit != null) {
            // 标签行单位（"工作单位：XXX"）置信度高，净化后直接落定
            unit = sanitizeUnit(unit, name);
            if (unit != null) fields.put("单位", unit);
        } else if (affiliatedUnit != null) {
            unit = affiliatedUnit;
        } else if (comboUnit == null) {
            // 2026-11-18 改为强/弱候选分级取最优：
            // 强=整行以机构后缀结尾（"洛阳市中心医院"、"医院河南科技大学第一附属医院"表格标签行）；
            // 弱=行中截断到机构后缀（"河南省医学会青年委员"→"河南省医学会"、"主治医师XX医院放疗科"→"主治医师XX医院"）。
            // 跳过：教育经历行（毕业/年份开头），防"2018年毕业于XX大学""2013/09-2018/07南方医科大学"误抓。
            // 2026-11-19 王俊生案例：任职行（"安阳市医学会肿瘤内科专业委员会主任委员"）是学术兼职
            // 不是任职单位，含委员/理事/主委/会员/秘书的行不再作为单位候选。
            String strong = null;
            String weak = null;
            for (String line : cleaned) {
                String compact = line.replace(" ", "");
                if (compact.length() > 40 || compact.contains("简历")) continue;
                if (compact.contains("毕业")) continue;
                if (compact.matches("\\d{4}.*")) continue;
                if (MEMBERSHIP_WORDS.stream().anyMatch(compact::contains)) continue;
                String lineStrong = null;
                String lineWeak = null;
                for (String suffix : ORG_SUFFIXES) {
                    if (compact.endsWith(suffix)) {
                        String cand = stripLeadingOrgEcho(compact);
                        if (cand.length() >= 4 && (lineStrong == null || betterOrgCandidate(cand, lineStrong))) {
                            lineStrong = cand;
                        }
                        break;
                    }
                    int idx = compact.indexOf(suffix);
                    if (idx >= 0) {
                        String cand = compact.substring(0, idx + suffix.length());
                        if (cand.length() >= 4 && (lineWeak == null || betterOrgCandidate(cand, lineWeak))) {
                            lineWeak = cand;
                        }
                    }
                }
                if (lineStrong != null && (strong == null || betterOrgCandidate(lineStrong, strong))) strong = lineStrong;
                if (lineWeak != null && (weak == null || betterOrgCandidate(lineWeak, weak))) weak = lineWeak;
            }
            unit = strong != null ? strong : weak;
        }
        unit = sanitizeUnit(unit, name);
        // 组合行/标签行已落定单位时不再覆盖；否则扫描候选（已净化）不直接落定，
        // 返回给调用方按"组合行>标签行>文件名>散行扫描"排序
        boolean comboHit = comboUnit != null;

        String dept = extractByLabel(cleaned, "科室", "Department", 2, 20);
        if (dept == null) dept = fields.get("科室");
        if (dept == null) {
            for (String line : cleaned) {
                String compact = line.replace(" ", "");
                if (compact.length() > 20 || compact.contains("简历") || compact.equals(name)) continue;
                if (unit != null && (compact.equals(unit) || unit.contains(compact))) continue;
                if (DEPT_SUFFIXES.stream().anyMatch(compact::endsWith)
                        && ORG_SUFFIXES.stream().noneMatch(compact::endsWith)) {
                    // 2026-11-18：行内含机构后缀时取其后尾段（"河南科技大学第一附属医院肿瘤内科"→"肿瘤内科"），
                    // 防止"主治医师XX医院放疗科"整行混入科室
                    String tail = compact;
                    for (String suffix : ORG_SUFFIXES) {
                        int idx = tail.indexOf(suffix);
                        if (idx >= 0) {
                            tail = tail.substring(idx + suffix.length());
                            break;
                        }
                    }
                    if (tail.length() >= 2 && tail.length() <= 10
                            && DEPT_SUFFIXES.stream().anyMatch(tail::endsWith)
                            && TITLE_WORDS.stream().noneMatch(tail::contains)) {
                        dept = tail;
                        break;
                    }
                }
            }
        }
        if (dept != null && !comboHit) fields.put("科室", dept);

        String title = extractByLabel(cleaned, "职称", "Title", 2, 20);
        if (title != null) title = canonicalTitle(title);
        if (title == null) title = extractTitleByLongestMatch(cleaned);
        if (title != null) fields.putIfAbsent("职称", title);

        Matcher phone = PHONE.matcher(full);
        if (phone.find()) fields.put("联系方式", phone.group());
        else {
            Matcher email = EMAIL.matcher(text);
            if (email.find()) fields.put("联系方式", email.group());
        }
        return comboHit ? null : unit;
    }

    /** 标签行解析：同行取分隔符后内容；单独标签行取下一个非空行。 */
    private String extractByLabel(List<String> lines, String cnLabel, String enLabel, int minLen, int maxLen) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int idx = indexOfLabel(line, cnLabel, enLabel);
            if (idx < 0) continue;
            String rest = line.substring(idx).replaceFirst("^(" + cnLabel + "|" + enLabel + ")[:：\\s]*", "").trim();
            if (rest.isEmpty()) {
                for (int j = i + 1; j < lines.size() && j <= i + 2; j++) {
                    if (!lines.get(j).isEmpty()) {
                        rest = lines.get(j).trim();
                        break;
                    }
                }
            }
            rest = rest.replaceAll("^[|｜\\-—]+", "").trim();
            if (rest.length() >= minLen && rest.length() <= maxLen) {
                return rest;
            }
        }
        return null;
    }

    private int indexOfLabel(String line, String cnLabel, String enLabel) {
        int cn = line.indexOf(cnLabel);
        int en = enLabel == null ? -1 : line.toUpperCase(Locale.ROOT).indexOf(enLabel.toUpperCase(Locale.ROOT));
        if (cn >= 0 && en >= 0) return Math.min(cn, en);
        return Math.max(cn, en);
    }

    private boolean isChineseWordChar(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    /**
     * 职称兜底（2026-11-17 修复词边界误拒；2026-11-19 改为词表顺序全局优先）：
     * 收集全部行命中的职称，取词表顺序最靠前者（Owner：主任医师、教授同行取主任医师；
     * 先"研究员"后"主任医师"也取主任医师）。子串命中被更长词覆盖时让位
     * （"副主任中医师"取整体而非"医师"）；尾边界保留（职称词后不得紧接汉字，
     * 防"护士长"命中"护士"）。无精确词时短行模糊匹配"主任/副主任/主治"→医师职称。
     * 对外契约不变：返回职称词或 null。
     */
    private String extractTitleByLongestMatch(List<String> lines) {
        String best = null;
        int bestOrder = Integer.MAX_VALUE;
        for (String line : lines) {
            String[] hit = scanTitleOrdered(line, null);
            if (hit == null) continue;
            int order = TITLE_WORDS.indexOf(hit[0]);
            if (order < bestOrder) {
                bestOrder = order;
                best = hit[0];
            }
        }
        if (best != null) return best;
        // 模糊匹配防误伤：仅限短行且不含科室特征字（"内科病区 副主任"这类科室行不得当职称）
        for (String line : lines) {
            String compact = line.replace(" ", "").replace("　", "");
            if (compact.length() > 12 || compact.chars().anyMatch(c -> "科部组室房".indexOf(c) >= 0)) continue;
            for (String[] alias : TITLE_ALIASES) {
                if (compact.contains(alias[0])) return alias[1];
            }
        }
        return null;
    }

    /**
     * 按词表顺序扫描职称（词表顺序即优先级）：首个满足尾边界且未被更长词覆盖的命中即返回。
     * 返回 {职称词, 命中位置}，无命中返回 null。供整行兜底与组合行（医师类过滤）共用。
     */
    String[] scanTitleOrdered(String text, java.util.function.Predicate<String> filter) {
        for (String word : TITLE_WORDS) {
            if (filter != null && !filter.test(word)) continue;
            int from = 0;
            while (true) {
                int idx = text.indexOf(word, from);
                if (idx < 0) break;
                // 尾边界：职称词后不得紧接汉字
                if (idx + word.length() >= text.length() || !isChineseWordChar(text.charAt(idx + word.length()))) {
                    boolean covered = false;
                    for (String longer : TITLE_WORDS) {
                        if (longer.length() <= word.length() || covered) continue;
                        int li = text.indexOf(longer);
                        if (li >= 0 && idx >= li && idx + word.length() <= li + longer.length()) covered = true;
                    }
                    if (!covered) return new String[]{word, String.valueOf(idx)};
                }
                from = idx + 1;
            }
        }
        return null;
    }

    /**
     * 职称候选归一（标签行"职称：XXX"与 LLM 结果共用）：取候选文本中包含的最长职称词；
     * 无精确词时模糊别名兜底（"主任"→主任医师）。返回词表内职称词或 null。
     */
    String canonicalTitle(String candidate) {
        if (candidate == null || candidate.isBlank()) return null;
        String normalized = candidate.replaceAll("\\s+", "");
        String best = null;
        for (String word : TITLE_WORDS) {
            if (!normalized.contains(word)) continue;
            if (best == null || word.length() > best.length()) best = word;
        }
        if (best != null) return best;
        for (String[] alias : TITLE_ALIASES) {
            if (normalized.contains(alias[0])) return alias[1];
        }
        return null;
    }

    /**
     * 剥除行首机构后缀回声（2026-11-18）：表格标签行"医院 河南科技大学第一附属医院"压缩后
     * 以"医院"开头，仅当剩余部分仍以机构后缀结尾时剥除标签，防整体误剥。
     */
    String stripLeadingOrgEcho(String compact) {
        for (String suffix : ORG_SUFFIXES) {
            if (compact.startsWith(suffix) && compact.length() > suffix.length()) {
                String rest = compact.substring(suffix.length());
                if (ORG_SUFFIXES.stream().anyMatch(rest::endsWith)) return rest;
            }
        }
        return compact;
    }

    /**
     * 机构候选排序（2026-11-18 王新帅原件回归）：医疗简历语境下"医院"后缀（含卫生院）
     * 优先于大学/中心/公司等——访问学者行"美国加州大学…医学中心"不得压过任职医院行；
     * 同后缀取更长候选。
     */
    boolean betterOrgCandidate(String candidate, String current) {
        boolean candHospital = candidate.endsWith("医院") || candidate.endsWith("卫生院");
        boolean curHospital = current.endsWith("医院") || current.endsWith("卫生院");
        if (candHospital != curHospital) return candHospital;
        return candidate.length() > current.length();
    }

    /**
     * 组合行匹配（2026-11-19 李宏案例）：识别"医院单位+科室+医师职称"任职陈述行，
     * 如"湖北省肿瘤医院微创介入科副主任医师"→ {湖北省肿瘤医院, 微创介入科, 副主任医师}。
     * 限定单位为医院/卫生院、职称为医师类，避免"美国加州大学医学中心助理教授"这类
     * 访学/学术行被误判为任职行（沿用 2026-11-18 王新帅案例口径）。
     * 返回 {单位, 科室(可为 null), 职称}；不匹配返回 null。
     */
    private static final String DEPARTMENT_SEGMENT =
            "([\\p{IsHan}]{1,11}?(?:科|病房|研究室|办公室|中心|部|组))";
    private static final String ADMIN_ROLE = "(?:行政)?(?:副主任|主任)";
    private static final Pattern HOSPITAL_DEPARTMENT = Pattern.compile(
            "([\\p{IsHan}]{2,28}(?:医院|卫生院))" + DEPARTMENT_SEGMENT + "(?:" + ADMIN_ROLE + ")?");
    private static final Pattern DEPARTMENT_FOLLOWED_BY_TITLE = Pattern.compile(
            DEPARTMENT_SEGMENT + "(?:" + ADMIN_ROLE + "[、，,])?[：:]?("
                    + TITLE_WORDS.stream().filter(word -> word.endsWith("医师"))
                            .collect(java.util.stream.Collectors.joining("|"))
                    + ").*");

    String[] matchUnitDeptTitle(String compact) {
        for (String orgSuffix : ORG_SUFFIXES) {
            boolean hospitalFamily = "医院".equals(orgSuffix) || "卫生院".equals(orgSuffix);
            if (!hospitalFamily) continue;
            int orgIdx = compact.indexOf(orgSuffix);
            if (orgIdx < 0) continue;
            String unit = compact.substring(0, orgIdx + orgSuffix.length());
            if (unit.length() < 4) continue;
            // 相邻行拼接防御：单位段混入学位/经历词（"…医学博士湖北省肿瘤医院"）说明跨了语义段，拒绝
            if (unit.matches(".*(博士|硕士|学士|研究生|奖学金|访问学者).*")) continue;
            String rest = compact.substring(orgIdx + orgSuffix.length());
            Matcher department = DEPARTMENT_FOLLOWED_BY_TITLE.matcher(rest);
            if (!department.matches()) continue;
            return new String[]{unit, department.group(1), department.group(2)};
        }
        return null;
    }

    /**
     * 单位字段净化（2026-11-17 修复"姓名混入单位"；2026-11-18 增加职称前缀剥离"主治医师XX医院"）：
     * 设计稿常把姓名与单位写在同一行（"李向东，汝州市第一人民医院…"），按机构后缀截断时
     * 会把前置姓名一并带入。此处剥离已知姓名前缀，并按分隔符取以机构后缀结尾的机构段，
     * 丢弃姓名/职称段。对外契约不变：返回净化后的单位字符串，无机构段时返回原值（交由上层校验）。
     */
    String sanitizeUnit(String raw, String knownName) {
        if (raw == null || raw.isBlank()) return null;
        String unit = raw.trim();
        // 剥离已解析出的姓名前缀（含其后分隔符，兼容"姓名，单位"与"姓名单位"直接拼接）
        if (knownName != null && !knownName.isBlank() && unit.startsWith(knownName)) {
            unit = unit.substring(knownName.length()).replaceFirst("^[，,、；;：:\\s／/|]+", "").trim();
        }
        // 剥除职称前缀（Ollama/截断候选常见"主治医师XX医院"），最长职称词优先
        for (String title : TITLE_WORDS.stream().sorted((a, b) -> b.length() - a.length()).toList()) {
            if (unit.startsWith(title) && unit.length() > title.length()) {
                String rest = unit.substring(title.length()).replaceFirst("^[，,、；;：:\\s／/|]+", "").trim();
                if (rest.contains("医院") || rest.contains("大学") || rest.contains("学院")
                        || ORG_SUFFIXES.stream().anyMatch(rest::endsWith)) {
                    unit = rest;
                }
                break;
            }
        }
        // 按分隔符切分，取以机构后缀结尾的最长段（姓名/职称段被丢弃）
        String best = null;
        for (String part : unit.split("[，,、；;／/|\\s]+")) {
            String p = part.trim();
            if (p.length() >= 4 && ORG_SUFFIXES.stream().anyMatch(p::endsWith)
                    && (best == null || p.length() > best.length())) {
                best = p;
            }
        }
        if (best != null) return best;
        return unit.isEmpty() ? null : unit;
    }

    // ==================== 头像候选 ====================

    /** 抠图结果缩放（保留 Alpha）：最长边超限时等比缩小并重编码 PNG。 */
    private byte[] downscalePng(byte[] png, int maxSide) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) throw new IOException("PNG 解析失败");
        int w = src.getWidth(), h = src.getHeight();
        int limit = Math.max(w, h);
        if (limit <= maxSide) return png;
        double scale = (double) maxSide / limit;
        BufferedImage out = new BufferedImage(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "png", bos)) throw new IOException("PNG 编码失败");
        return bos.toByteArray();
    }

    /** 候选原图转无损 PNG bytes（保留像素，供 rembg 抠图）。 */
    private byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", bos) || bos.size() == 0) throw new IOException("PNG 编码失败");
        return bos.toByteArray();
    }

    /** 候选图统一重绘到 TYPE_INT_RGB 白底（JPEG 编码器不支持带 Alpha 的颜色模型）并缩到最长边 600px。 */
    private String toBase64Jpeg(BufferedImage img) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        int maxSide = Math.max(w, h);
        double scale = maxSide > 600 ? 600.0 / maxSide : 1.0;
        BufferedImage out = new BufferedImage((int) (w * scale), (int) (h * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "jpg", bos)) return "";   // 无编码器时返回空串，由调用方过滤
        byte[] encoded = bos.toByteArray();
        return encoded.length == 0 ? "" : Base64.getEncoder().encodeToString(encoded);
    }

    private List<BufferedImage> extractPdfImages(PDDocument doc) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        int pageIndex = 0;
        for (PDPage page : doc.getPages()) {
            if (pageIndex >= 2 || images.size() >= 10) break;
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (var name : resources.getXObjectNames()) {
                try {
                    if (resources.getXObject(name) instanceof PDImageXObject image) {
                        collectImage(images, toJpegBytes(image.getImage()));
                    }
                } catch (Exception ignored) {
                    // 单个图片对象损坏不影响其余候选
                }
            }
            pageIndex++;
        }
        return images;
    }

    private byte[] toJpegBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

}
