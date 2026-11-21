package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectShowService {
    public static final String FILE_PREFIX = "project_show_";
    private static final Pattern TIME = Pattern.compile("\\d{1,2}[:：]\\d{2}");
    private static final Pattern AGENDA_MARKER = Pattern.compile("茶歇|休息|签到|开幕|开场|致辞|闭幕|合影|午餐|晚餐|会议总结");
    private static final Pattern IMAGE = Pattern.compile("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}");
    private static final int MAX_DECK_SIZE = 10 * 1024 * 1024;

    public record Slide(String label, String title, String body, String image) {}
    public record Deck(String background, List<Slide> slides) {}

    private final ProjectService projects;
    private final ScheduleParseService schedules;

    public List<String> agenda(Long id, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(id), user);
        var schedule = schedules.currentSchedule(id);
        if (schedule == null) return List.of();
        byte[] content = projects.storageRead(schedule.getFilePath());
        if (content == null) throw new BusinessException("原始日程文件不存在");
        try {
            var extracted = schedules.extract(schedule.getFileName(), content);
            List<String> rows = extracted.tableRows() == null ? List.of() : extracted.tableRows().stream()
                    .map(cells -> String.join(" · ", cells.stream().filter(cell -> cell != null && !cell.isBlank()).toList()))
                    .filter(ProjectShowService::agendaLine).distinct().limit(150).toList();
            if (!rows.isEmpty()) return rows;
            return extracted.plainText().lines().map(String::strip).filter(ProjectShowService::agendaLine)
                    .distinct().limit(150).toList();
        } catch (IOException e) {
            throw new BusinessException("原始日程提取失败");
        }
    }

    private static boolean agendaLine(String line) {
        return TIME.matcher(line).find() || AGENDA_MARKER.matcher(line).find();
    }

    @Transactional
    public void upload(Long id, MultipartFile file, AuthUser user) throws IOException {
        Project project = currentShow(id, user);
        String name = file.getOriginalFilename();
        String ext = com.jshz.expert.common.FileNames.extension(name).toLowerCase(Locale.ROOT);
        if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024 || !List.of("ppt", "pptx").contains(ext)) {
            throw new BusinessException("请选择不超过 50 MB 的 PPT 或 PPTX 文件");
        }
        try (var input = file.getInputStream()) {
            if ("pptx".equals(ext)) {
                try (var slides = new org.apache.poi.xslf.usermodel.XMLSlideShow(input)) {
                    if (slides.getSlides().isEmpty()) throw new IllegalArgumentException();
                }
            } else {
                try (var slides = new org.apache.poi.hslf.usermodel.HSLFSlideShow(input)) {
                    if (slides.getSlides().isEmpty()) throw new IllegalArgumentException();
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("无法读取有效的 PowerPoint 文件");
        }
        projects.archiveStageDocument(id, project.getPhaseId(), FILE_PREFIX, ext, "串场制作." + ext,
                file.getBytes(), user, false);
        projects.operationRecord(user, "归档串场 PowerPoint", "PROJECT", id);
    }

    @Transactional
    public void publish(Long id, Deck deck, AuthUser user) {
        Project project = currentShow(id, user);
        String html = render(deck);
        projects.archiveStageDocument(id, project.getPhaseId(), FILE_PREFIX, "html", "串场制作.html",
                html.getBytes(StandardCharsets.UTF_8), user, false);
        projects.operationRecord(user, "归档串场网页幻灯片", "PROJECT", id);
    }

    private Project currentShow(Long id, AuthUser user) {
        Project project = projects.requireProject(id);
        projects.requireOperable(project, user);
        if (!"串场制作".equals(projects.phaseNameOf(project))) throw new BusinessException("只可在串场制作阶段归档");
        return project;
    }

    static void validateDraft(Deck deck) {
        render(deck);
    }

    static String render(Deck deck) {
        if (deck == null || deck.slides() == null || deck.slides().isEmpty() || deck.slides().size() > 180) {
            throw new BusinessException("请保留 1 至 180 张幻灯片");
        }
        String background = image(deck.background());
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
                <title>会议串场</title>
                <style>
                *{box-sizing:border-box}body{margin:0;background:#101827;color:white;font-family:system-ui,sans-serif}
                main{height:calc(100dvh - 3rem);display:grid;place-items:center;container-type:size}
                section{position:relative;width:min(100cqw,177.78cqh);aspect-ratio:16/9;overflow:auto;padding:7%;background:linear-gradient(135deg,#0e3153,#145371);font-size:2.1cqw}
                section[hidden]{display:none}.theme{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
                article{position:relative;white-space:pre-wrap;overflow-wrap:anywhere;text-shadow:0 2px 6px #111}
                h1{font-size:2.35em;margin:.35em 0}p{margin:0}.portrait{width:24%;max-height:55%;object-fit:contain;float:right;margin-left:4%}
                nav{height:3rem;display:flex;justify-content:center;align-items:center;gap:1rem}button{font:inherit;padding:.25rem .6rem}
                </style>
                <main>
                """);
        // The shared background is emitted once and applied to each slide by the offline player.
        if (!background.isEmpty()) html.append("<template id=\"theme\"><img class=\"theme\" alt=\"\" src=\"").append(background).append("\"></template>");
        for (Slide slide : deck.slides()) {
            if (slide == null) throw new BusinessException("幻灯片内容无效");
            String portrait = image(slide.image());
            html.append("<section hidden><article>");
            if (!portrait.isEmpty()) html.append("<img class=\"portrait\" alt=\"\" src=\"").append(portrait).append("\">");
            html.append("<p>").append(text(slide.label(), 160)).append("</p><h1>").append(text(slide.title(), 240))
                    .append("</h1><p>").append(text(slide.body(), 4000)).append("</p></article></section>");
            if (html.length() > MAX_DECK_SIZE) throw new BusinessException("串场文件过大，请压缩图片");
        }
        return html.append("""
                </main><nav aria-label="播放控制"><button id="prev">上一页</button>
                <output id="count"></output><button id="next">下一页</button><button id="full">全屏</button></nav>
                <script>
                const slides=Array.from(document.querySelectorAll('section'));
                const theme=document.getElementById('theme');
                const count=document.getElementById('count');
                let page=0;
                function show(index) {
                  slides[page].hidden=true;
                  page=Math.max(0,Math.min(slides.length-1,index));
                  if(theme && !slides[page].querySelector('.theme')) slides[page].prepend(theme.content.cloneNode(true));
                  slides[page].hidden=false;
                  count.textContent=(page+1)+' / '+slides.length;
                }
                document.getElementById('prev').onclick=()=>show(page-1);
                document.getElementById('next').onclick=()=>show(page+1);
                document.getElementById('full').onclick=()=>document.documentElement.requestFullscreen?.().catch(()=>{});
                document.addEventListener('keydown',event=>{
                  if(event.target instanceof HTMLButtonElement && (event.key===' ' || event.key==='Enter')) return;
                  if(['ArrowRight','PageDown',' '].includes(event.key)) {event.preventDefault();show(page+1);}
                  if(['ArrowLeft','PageUp'].includes(event.key)) {event.preventDefault();show(page-1);}
                  if(event.key==='Home') show(0);
                  if(event.key==='End') show(slides.length-1);
                });
                show(0);
                </script></html>
                """).toString();
    }

    private static String text(String value, int limit) {
        if (value == null) return "";
        if (value.length() > limit) throw new BusinessException("幻灯片文字超出长度限制");
        return HtmlUtils.htmlEscape(value);
    }

    private static String image(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() > 3 * 1024 * 1024 || !IMAGE.matcher(value).matches()) {
            throw new BusinessException("图片须为 PNG、JPEG 或 WebP，单张不超过 2 MB");
        }
        return value;
    }
}
