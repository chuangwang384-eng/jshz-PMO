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
import java.util.Arrays;
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

    /**
     * deck.template 取 {@link #THEMES} 中的 id，缺失或未知时回退 gold；fontScale 允许范围 0.8~1.4，
     * 缺失时按 1.0。photos 为照片池（去重后的 dataURL），页面内 image 字段可用 "ph:{序号}" 引用池中照片，
     * 避免一位专家出现在多个环节时照片重复嵌入撑爆体积；旧草稿无 photos、image 直接存 dataURL，两者兼容。
     */
    public record Deck(String background, List<Slide> slides, String template, Double fontScale, List<String> photos) {}

    /** 页面人物（2026-11-21 结构化页面模型）：出场页/讨论墙/过场页共用。image 为已抠图 dataURL，可空。 */
    public record Person(String role, String name, String title, String unit, String image) {}

    /**
     * 结构化页面（2026-11-21）：kind 取 cover/section/bio/resume/panel/agenda/ending/content，
     * 缺失时回退 label 启发式（兼容 1.0.103 及以前的扁平草稿）。items 承载封面信息行（"k|v"）、
     * 章节编号（首元素）、简历条目；persons 承载页面人物。
     */
    public record Slide(String label, String title, String body, String image,
                        String kind, List<String> items, List<Person> persons, Double fontSize) {}

    /** 内置串场模板。css 只允许覆盖调色变量与装饰层，页面结构与动画引擎由 render 的公共层承载，禁止复制私有副本。 */
    public record Theme(String id, String name, String css, boolean particles) {}

    private static final Theme GOLD = new Theme("gold", "红金盛典", """
            :root{--bg:radial-gradient(1500px 1000px at 50% 42%,#7e1414 0%,#5d0e0e 46%,#3c0707 100%);
            --deco:radial-gradient(520px 520px at -140px -160px,rgba(255,196,110,.2),transparent 68%),
            radial-gradient(640px 640px at calc(100% + 200px) calc(100% + 220px),rgba(255,150,80,.16),transparent 68%),
            linear-gradient(90deg,transparent,rgba(255,214,140,.06) 45%,rgba(255,236,190,.1) 50%,rgba(255,214,140,.06) 55%,transparent);
            --flow:linear-gradient(100deg,#8a5a1e,#f2c66d 22%,#fff4d6 38%,#f7d98c 50%,#caa050 72%,#8a5a1e);
            --ink:#fff3dc;--ink-dim:#f3d9ac;--accent:#f2c66d;--rule:rgba(242,198,109,.5);--card:rgba(255,243,220,.07);
            --font-title:"STZhongsong","NSimSun","SimSun",serif;--font-body:"Microsoft YaHei UI","PingFang SC",sans-serif;
            --particle:255,214,140}
            h1,h2{font-family:var(--font-title)}
            """, true);

    private static final Theme TECH = new Theme("tech", "深蓝科技", """
            :root{--bg:radial-gradient(1400px 900px at 50% 30%,#0d2f4f 0%,#081d33 55%,#04101d 100%);
            --deco:repeating-linear-gradient(0deg,rgba(88,183,255,.05) 0 1px,transparent 1px 72px),
            repeating-linear-gradient(90deg,rgba(88,183,255,.05) 0 1px,transparent 1px 72px),
            radial-gradient(600px 400px at 15% 10%,rgba(88,183,255,.16),transparent 65%),
            radial-gradient(700px 500px at 90% 92%,rgba(46,214,255,.1),transparent 65%);
            --flow:linear-gradient(100deg,#1d5c8f,#58b7ff 25%,#e4f6ff 50%,#58b7ff 75%,#1d5c8f);
            --ink:#eaf4ff;--ink-dim:#9fc6e8;--accent:#58b7ff;--rule:rgba(88,183,255,.45);--card:rgba(234,244,255,.07);
            --font-title:"Microsoft YaHei UI","PingFang SC",sans-serif;--font-body:"Microsoft YaHei UI","PingFang SC",sans-serif;
            --particle:120,200,255}
            """, true);

    private static final Theme PAPER = new Theme("paper", "商务简白", """
            :root{--bg:linear-gradient(180deg,#f7f8fb,#eef1f6);
            --deco:linear-gradient(180deg,#254a8f 0 4px,transparent 4px),
            radial-gradient(700px 420px at 88% 96%,rgba(37,99,199,.08),transparent 70%),
            radial-gradient(500px 320px at 6% 8%,rgba(201,166,74,.08),transparent 70%);
            --flow:linear-gradient(100deg,#1f3d7a,#2563c7 25%,#8db9ff 50%,#2563c7 75%,#1f3d7a);
            --ink:#1f2329;--ink-dim:#5f6673;--accent:#2563c7;--rule:rgba(31,61,122,.35);--card:rgba(31,61,122,.06);
            --font-title:"Microsoft YaHei UI","PingFang SC",sans-serif;--font-body:"Microsoft YaHei UI","PingFang SC",sans-serif;
            --particle:37,99,199}
            """, false);

    private static final Theme BAMBOO = new Theme("bamboo", "青竹学术", """
            :root{--bg:radial-gradient(1300px 850px at 50% 36%,#f2f9f4 0%,#e2f0e7 55%,#d3e7db 100%);
            --deco:linear-gradient(180deg,#2f8f6b 0 4px,transparent 4px),
            radial-gradient(620px 380px at 90% 12%,rgba(47,143,107,.1),transparent 70%),
            radial-gradient(560px 360px at 8% 92%,rgba(47,143,107,.09),transparent 70%);
            --flow:linear-gradient(100deg,#1d5c46,#2f8f6b 25%,#b9ecd2 50%,#2f8f6b 75%,#1d5c46);
            --ink:#17352a;--ink-dim:#52705f;--accent:#2f8f6b;--rule:rgba(29,92,70,.35);--card:rgba(23,53,42,.06);
            --font-title:"STZhongsong","NSimSun",serif;--font-body:"Microsoft YaHei UI","PingFang SC",sans-serif;
            --particle:47,143,107}
            """, false);

    private static final List<Theme> THEMES = List.of(GOLD, TECH, PAPER, BAMBOO);

    static Theme themeOf(String id) {
        for (Theme theme : THEMES) {
            if (theme.id().equals(id)) return theme;
        }
        return GOLD;
    }

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

    /** 只读预览：把编辑器当前草稿渲染为离线 HTML 返回，不落盘、不归档；预览与归档共用 render，保证产物一致。 */
    public String preview(Long id, Deck deck, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(id), user);
        return render(deck);
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
        Theme theme = themeOf(deck.template());
        double fs = deck.fontScale() == null ? 1.0 : Math.max(0.8, Math.min(1.4, deck.fontScale()));
        String background = image(deck.background());
        if (deck.photos() != null) {
            if (deck.photos().size() > 300) throw new BusinessException("照片数量过多，请精简专家资料");
            deck.photos().forEach(ProjectShowService::image);
        }
        StringBuilder html = new StringBuilder(head(theme, fs, background));
        for (int i = 0; i < deck.slides().size(); i++) {
            Slide slide = deck.slides().get(i);
            if (slide == null) throw new BusinessException("幻灯片内容无效");
            html.append(slideHtml(resolvePhotos(slide, deck.photos()), i));
            if (html.length() > MAX_DECK_SIZE) throw new BusinessException("串场文件过大，请压缩图片");
        }
        return html.append(deckTail(theme)).toString();
    }

    /** 把 "ph:{序号}" 引用解析为照片池中的实际 dataURL；直接存 dataURL 的旧结构原样返回。 */
    private static Slide resolvePhotos(Slide slide, List<String> photos) {
        if (photos == null || photos.isEmpty()) return slide;
        String img = photoOf(slide.image(), photos);
        List<Person> persons = slide.persons() == null ? null : slide.persons().stream()
                .map(p -> p == null ? null
                        : new Person(p.role(), p.name(), p.title(), p.unit(), photoOf(p.image(), photos)))
                .toList();
        return new Slide(slide.label(), slide.title(), slide.body(), img, slide.kind(), slide.items(), persons, slide.fontSize());
    }

    private static String photoOf(String image, List<String> photos) {
        if (image == null || !image.startsWith("ph:")) return image;
        int i;
        try {
            i = Integer.parseInt(image.substring(3));
        } catch (NumberFormatException e) {
            return "";
        }
        if (i < 0 || i >= photos.size()) return "";
        return photos.get(i);
    }

    private static final String CSP = "<meta http-equiv=\"Content-Security-Policy\" "
            + "content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'\">";

    /** 公共结构与动画引擎 CSS；主题只经 :root 变量注入。 */
    private static final String BASE_CSS = """
            *{box-sizing:border-box}
            html,body{margin:0;height:100%;overflow:hidden}
            body{font-family:var(--font-body);color:var(--ink);background:var(--bg)}
            body::before{content:"";position:fixed;inset:0;background:var(--deco);pointer-events:none;z-index:0}
            body.k-on{--ink:#ffffff;--ink-dim:rgba(255,255,255,.78)}
            #fx{position:fixed;inset:0;z-index:1;pointer-events:none}
            #deck{position:fixed;left:50%;top:50%;width:1920px;height:1080px;transform:translate(-50%,-50%);
              font-size:calc(17px*var(--fs,1));z-index:2}
            .slide{position:absolute;inset:0;display:none}
            .slide.active{display:block}
            .k-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
            .veil{position:absolute;inset:0;background:rgba(10,12,16,.45)}
            .wrap{position:relative;height:100%;padding:6% 7%;display:flex;flex-direction:column;justify-content:center}
            .wrap.center{align-items:center;text-align:center}
            .crumb{align-self:flex-start;width:fit-content;font-size:1.05em;letter-spacing:.2em;color:var(--ink-dim);
              border:1px solid var(--rule);border-radius:999px;padding:.35em 1.1em;margin:0 0 1.2em}
            .org{font-size:1.6em;letter-spacing:.42em;text-indent:.42em;color:var(--ink-dim);margin:0 0 1.6em}
            .flow{background:var(--flow);background-size:220% 100%;-webkit-background-clip:text;background-clip:text;
              color:transparent;animation:shine 6s linear infinite;font-family:var(--font-title);margin:.2em 0}
            h1.flow{font-size:3.4em;line-height:1.2;overflow-wrap:anywhere}
            .k-cover h1.flow,.k-ending h1.flow{font-size:7.2em;line-height:1.15}
            h1.flow.t-long{font-size:2.5em}
            .k-cover h1.flow.t-long,.k-ending h1.flow.t-long{font-size:4.6em}
            .rule{width:min(520px,62%);height:2px;margin:1.6em 0;position:relative;
              background:linear-gradient(90deg,transparent,var(--accent) 32%,var(--accent) 68%,transparent)}
            .wrap.center .rule{margin:1.6em auto}
            .rule::after{content:"";position:absolute;left:50%;top:50%;width:12px;height:12px;
              transform:translate(-50%,-50%) rotate(45deg);background:var(--accent);
              box-shadow:0 0 14px var(--rule);animation:pulse 2.6s ease-in-out infinite}
            .info{font-size:1.5em;letter-spacing:.1em;color:var(--ink-dim);margin:.5em 0 0}
            .lines{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:.6em;font-size:1.5em}
            .lines li{display:flex;align-items:baseline;gap:.55em;overflow-wrap:anywhere;color:var(--ink)}
            .lines li::before{content:"";flex:none;width:.55em;height:.55em;transform:rotate(45deg) translateY(-.1em);
              background:var(--accent);box-shadow:0 0 10px var(--rule)}
            /* 旧版左右人物布局（兼容 1.0.103 前扁平草稿） */
            .bio{flex-direction:row;align-items:center;gap:5%}
            .photo{flex:none;width:23%;aspect-ratio:1/1;margin:0;position:relative;overflow:hidden;
              border:2px solid var(--rule);border-radius:50%;
              box-shadow:0 18px 48px rgba(0,0,0,.35)}
            .photo img{width:100%;height:100%;object-fit:cover;object-position:center top;display:block}
            .photo::after{content:"";position:absolute;top:-60%;left:0;width:34%;height:220%;
              transform:translateX(-180%) rotate(22deg);pointer-events:none;
              background:linear-gradient(90deg,transparent,rgba(255,255,255,.22),transparent);
              animation:sweep 5.5s ease-in-out infinite}
            .bio-main{flex:1;min-width:0}
            .bio-main .crumb{margin-bottom:1em}
            /* ===== 结构化页面（2026-11-21）：cover/section/bio-v/resume/panel ===== */
            .cover{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;
              text-align:center;position:relative;padding:0 7%}
            .cover-emblem{width:104px;height:104px;border-radius:50%;border:2px solid var(--rule);
              position:relative;margin-bottom:26px}
            .cover-emblem::before{content:"";position:absolute;inset:-16px;border-radius:50%;
              border:1px dashed var(--rule);animation:spin 26s linear infinite}
            .cover-emblem i{position:absolute;inset:22px;border-radius:50%;
              background:radial-gradient(circle at 34% 30%,var(--ink),var(--accent) 55%);
              box-shadow:0 0 34px var(--rule)}
            .cover-org{font-size:1.7em;letter-spacing:.42em;text-indent:.42em;color:var(--ink-dim);margin:0 0 30px}
            .cover-title{font-size:9.2em;line-height:1.08;margin:.06em 0;overflow-wrap:anywhere;max-width:100%}
            .cover-title.t-long{font-size:5.4em}
            .cover-sub{font-size:1.9em;letter-spacing:.45em;text-indent:.45em;color:var(--ink-dim);margin:16px 0 0}
            .cover-line{width:520px;height:2px;margin:42px 0 34px;position:relative;
              background:linear-gradient(90deg,transparent,var(--accent) 30%,var(--accent) 70%,transparent)}
            .cover-line::after{content:"";position:absolute;left:50%;top:50%;width:12px;height:12px;
              transform:translate(-50%,-50%) rotate(45deg);background:var(--accent);box-shadow:0 0 14px var(--rule)}
            .cover-info{display:flex;flex-direction:column;gap:14px}
            .ci-row{display:flex;align-items:baseline;gap:14px;font-size:1.5em;overflow-wrap:anywhere}
            .ci-k{color:var(--ink-dim);letter-spacing:.2em;flex:none}
            .ci-k::after{content:"｜";color:var(--rule);margin-left:6px}
            .ci-v{color:var(--ink)}
            .cover-corner{position:absolute;width:150px;height:150px;border:2px solid var(--rule);opacity:.7}
            .cover-corner.tl{left:56px;top:52px;border-right:none;border-bottom:none}
            .cover-corner.br{right:56px;bottom:52px;border-left:none;border-top:none}
            .sec{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;
              text-align:center;position:relative;overflow:hidden}
            .sec-num{font-size:11em;font-family:Georgia,serif;font-weight:700;line-height:1;color:transparent;
              -webkit-text-stroke:2px var(--accent);margin-bottom:6px;text-shadow:0 0 60px var(--rule)}
            .sec-title{font-size:6.6em;letter-spacing:.35em;text-indent:.35em;margin:.08em 0;font-weight:700;
              overflow-wrap:anywhere}
            .sec-line{width:420px;height:2px;margin:34px 0 28px;
              background:linear-gradient(90deg,transparent,var(--accent) 30%,var(--accent) 70%,transparent)}
            .sec-person strong{font-size:2.1em;letter-spacing:.15em;color:var(--ink)}
            .sec-person small{font-size:1.1em;color:var(--ink-dim);margin-left:.4em}
            .sec-person span{display:block;font-size:1.3em;color:var(--ink-dim);margin-top:10px;letter-spacing:.12em}
            .sec-wm{position:absolute;right:30px;bottom:-46px;font-size:300px;line-height:1;color:transparent;
              -webkit-text-stroke:1.5px var(--rule);opacity:.28;font-family:var(--font-title);
              pointer-events:none;user-select:none}
            .bio-v{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;
              text-align:center;padding:0 8%}
            .photo-card{width:30%;aspect-ratio:1/1;margin:0;position:relative;overflow:hidden;
              border:2px solid var(--rule);border-radius:50%;box-shadow:0 18px 48px rgba(0,0,0,.35)}
            .photo-card img{width:100%;height:100%;object-fit:cover;object-position:center top;display:block}
            .photo-card::after{content:"";position:absolute;top:-60%;left:0;width:34%;height:220%;
              transform:translateX(-180%) rotate(22deg);pointer-events:none;
              background:linear-gradient(90deg,transparent,rgba(255,255,255,.22),transparent);
              animation:sweep 5.5s ease-in-out infinite}
            .role-badge{font-size:1.35em;letter-spacing:.3em;text-indent:.3em;color:var(--ink-dim);
              border:1px solid var(--rule);border-radius:999px;padding:.32em 1.6em;margin:28px 0 14px}
            .bio-name{font-size:5.2em;line-height:1.15}
            .bio-name small{font-size:.42em;color:var(--ink-dim);margin-left:.25em;font-family:var(--font-body)}
            .bio-unit{font-size:1.5em;color:var(--ink-dim);letter-spacing:.15em;margin-top:10px}
            .resume-page{height:100%;padding:6.5% 8%;display:flex;flex-direction:column}
            .rp2{height:100%;display:flex;gap:4.5%;padding:6.5% 7%}
            .rp2-left{flex:none;width:23%;display:flex;flex-direction:column;align-items:center;
              text-align:center;justify-content:center}
            .rp2-avatar{width:88%;aspect-ratio:1/1;border-radius:50%;object-fit:cover;object-position:center top;
              border:2px solid var(--rule);box-shadow:0 14px 36px rgba(0,0,0,.3);display:block}
            .rp2-name{font-size:2.5em;margin:.45em 0 .05em;line-height:1.2;overflow-wrap:anywhere;text-align:center}
            .rp2-name small{font-size:.42em;color:var(--ink-dim);margin-left:.2em;font-family:var(--font-body)}
            .rp2-unit{font-size:1.25em;color:var(--ink-dim);margin:8px 0 0;letter-spacing:.1em;line-height:1.5}
            .rp2-left .role-badge{margin:22px 0 12px;font-size:1.2em}
            .rp2-main{flex:1;min-width:0;display:flex;flex-direction:column}
            .rp2-head{font-size:2.5em;margin:0;font-family:var(--font-title);letter-spacing:.1em}
            .rp2-head span:first-child{color:var(--ink)}
            .rp2-head span:last-child{font-size:.4em;letter-spacing:.6em;color:var(--ink-dim);margin-left:18px;font-family:var(--font-body)}
            .rp-rule{height:2px;background:linear-gradient(90deg,var(--accent),transparent 85%);margin:16px 0 34px}
            .rp2-list{flex:1;display:flex;flex-direction:column;gap:.55em;
              font-size:calc(1.4em*var(--rfs,1));list-style:none;margin:0;padding:0}
            .rp2-list.two{columns:2;column-gap:56px;display:block}
            .rp2-list li{display:flex;gap:.5em;break-inside:avoid;overflow-wrap:anywhere;line-height:1.45;
              margin-bottom:22px;color:var(--ink)}
            .rp2-list li::before{content:"";flex:none;width:.5em;height:.5em;transform:rotate(45deg);
              background:var(--accent);margin-top:.32em;box-shadow:0 0 8px var(--rule)}
            .sec-people{display:flex;justify-content:center;gap:2.4%;margin-top:22px;flex-wrap:wrap}
            .sec-p{display:flex;flex-direction:column;align-items:center;gap:8px;margin:0}
            .sec-p-avatar{width:110px;height:110px;display:flex;align-items:center;justify-content:center}
            .sec-p-avatar img{width:100%;height:100%;border-radius:50%;object-fit:cover;object-position:center top;
              border:2px solid var(--rule);box-shadow:0 10px 24px rgba(0,0,0,.3)}
            .sec-p-word{width:100%;height:100%;border-radius:50%;border:2px solid var(--rule);
              display:flex;align-items:center;justify-content:center;font-size:2.6em;color:var(--ink-dim);
              font-family:var(--font-title)}
            .sec-p figcaption{font-size:1.15em;color:var(--ink);letter-spacing:.08em}
            .sec-p figcaption small{color:var(--ink-dim);margin-left:.2em}
            .fs-bar{display:none;position:absolute;right:26px;top:22px;z-index:8;gap:8px}
            body.hppt-editing .slide.active.k-resume .fs-bar{display:flex}
            .fs-bar button{font:inherit;font-size:16px;padding:4px 14px;cursor:pointer;color:var(--ink);
              background:rgba(10,12,16,.8);border:1px solid var(--accent);border-radius:8px}
            .fs-bar button:hover{background:var(--accent);color:#10131a}
            .panel{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;
              text-align:center;padding:0 6%}
            .panel-eyebrow{font-size:1.15em;letter-spacing:.5em;text-indent:.5em;color:var(--ink-dim);
              margin:0 0 14px;text-transform:uppercase}
            .panel h1.flow{font-size:5.4em}
            .panel-cards{display:flex;flex-wrap:wrap;justify-content:center;gap:2% 2.2%;margin-top:2.6%;width:100%}
            .p-card{flex:0 0 21%;max-width:21%;background:var(--card);border:1px solid var(--rule);
              border-radius:16px;padding:5% 3%;display:flex;flex-direction:column;align-items:center}
            .p-avatar{width:74%;aspect-ratio:1/1;border-radius:50%;object-fit:cover;object-position:center top;
              border:2px solid var(--rule);display:block}
            .p-avatar-word{width:74%;aspect-ratio:1/1;border-radius:50%;border:2px solid var(--rule);
              display:flex;align-items:center;justify-content:center;font-size:3.6em;color:var(--ink-dim);
              font-family:var(--font-title);background:var(--card)}
            .p-card figcaption strong{display:block;font-size:1.65em;margin-top:.55em;letter-spacing:.06em}
            .p-card figcaption small{font-size:.62em;color:var(--ink-dim);margin-left:.25em}
            .p-card figcaption span{display:block;font-size:1.15em;color:var(--ink-dim);margin-top:.3em}
            .slide [data-step]{transition:opacity .6s cubic-bezier(.22,.85,.3,1),transform .6s cubic-bezier(.22,.85,.3,1),filter .6s ease}
            .slide [data-step].ppt-step-hidden{opacity:0;transform:translateY(30px) scale(.98);filter:blur(6px)}
            .a-in{animation:aIn .7s cubic-bezier(.22,.85,.3,1) both}
            .a-wide{animation:aWide .9s cubic-bezier(.22,.85,.3,1) both}
            #hud{position:fixed;left:0;right:0;bottom:0;height:46px;display:flex;justify-content:center;
              align-items:center;gap:14px;z-index:5;font-size:14px;color:var(--ink-dim)}
            #hud button{font:inherit;padding:3px 14px;cursor:pointer;color:inherit;background:transparent;
              border:1px solid var(--rule);border-radius:999px;opacity:.8}
            #hud button:hover{opacity:1}
            #prog{width:220px;height:4px;background:var(--rule);border-radius:2px;overflow:hidden;opacity:.5}
            #prog i{display:block;height:100%;width:0;background:var(--accent)}
            @keyframes shine{to{background-position:220% center}}
            @keyframes aIn{from{opacity:0;transform:translateY(30px);filter:blur(8px)}to{opacity:1;transform:none;filter:none}}
            @keyframes aWide{from{opacity:0;letter-spacing:.5em;filter:blur(10px)}to{opacity:1;transform:none;filter:none}}
            @keyframes pulse{0%,100%{opacity:.45}50%{opacity:1}}
            @keyframes sweep{from{transform:translateX(-180%) rotate(22deg)}to{transform:translateX(420%) rotate(22deg)}}
            @keyframes spin{to{transform:rotate(360deg)}}
            /* 编辑模式（所见即所得）：按 E 切换 contentEditable，虚线框标记可编辑元素 */
            [contenteditable="true"]{outline:1.5px dashed var(--accent);outline-offset:4px;border-radius:4px;cursor:text}
            body.hppt-editing [data-edit]{min-width:1em;display:inline-block}
            #editTip{display:none;position:fixed;top:14px;left:50%;transform:translateX(-50%);z-index:9;
              background:rgba(10,12,16,.85);color:var(--ink);border:1px solid var(--accent);border-radius:999px;
              padding:8px 22px;font-size:15px;letter-spacing:.08em}
            """;

    private static String head(Theme theme, double fs, String background) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                """).append(CSP).append("\n<title>会议串场</title>\n<style>\n")
                .append(BASE_CSS)
                .append(theme.css())
                .append("\n:root{--fs:").append(fs).append("}\n</style></head>\n<body class=\"")
                .append(background.isEmpty() ? "" : "k-on ").append("t-").append(theme.id()).append("\">\n");
        if (theme.particles()) html.append("<canvas id=\"fx\"></canvas>\n");
        // 主 K 只输出一次，由脚本克隆进每一页，避免 dataURL 乘以页数撑爆体积。
        if (!background.isEmpty()) {
            html.append("<template id=\"theme\"><img class=\"k-bg\" alt=\"\" src=\"").append(background)
                    .append("\"><span class=\"veil\"></span></template>\n");
        }
        return html.append("<div id=\"deck\">\n").toString();
    }

    /** 所见即所得编辑锚点：向导预览按此 data-edit 路径把页面文字写回 deck（p{页序}.{字段路径}）。 */
    private static String ed(int idx, String path) {
        return " data-edit=\"p" + idx + "." + path + "\"";
    }

    private static String slideHtml(Slide slide, int idx) {
        boolean first = idx == 0;
        String label = text(slide.label(), 160, "页面标签");
        String title = text(slide.title(), 240, "页面标题");
        String body = text(slide.body(), 4000, "页面正文");
        String portrait = image(slide.image());
        String kind = slide.kind() == null || slide.kind().isBlank()
                ? kindOf(label, title, portrait, first) : slide.kind();
        List<String> lines = body.isBlank() ? List.of() : Arrays.stream(body.split("\n"))
                .map(String::strip).filter(line -> !line.isEmpty()).toList();
        return switch (kind) {
            case "cover" -> coverHtml(idx, label, title, lines, slide.items());
            case "section" -> sectionHtml(idx, label, title, slide.items(), slide.persons());
            case "resume" -> resumeHtml(idx, label, title, slide.items(), slide.persons(), slide.fontSize());
            case "panel" -> panelHtml(idx, label, title, slide.persons());
            case "bio" -> structuredBio(slide.persons()) != null
                    ? bioHtml(idx, label, structuredBio(slide.persons())) : legacyContentHtml(idx, kind, label, title, portrait, lines);

            default -> legacyContentHtml(idx, kind, label, title, portrait, lines);
        };
    }

    /** 出场页主人物：persons 非空取首个有姓名的；否则 null 走旧结构渲染。 */
    private static Person structuredBio(List<Person> persons) {
        if (persons == null) return null;
        for (Person p : persons) {
            if (p != null && p.name() != null && !p.name().isBlank()) return p;
        }
        return null;
    }

    private static String bioHtml(int idx, String label, Person p) {
        String portrait = image(p.image());
        String role = text(p.role(), 40, "大会角色");
        String name = text(p.name(), 60, "姓名");
        String title = text(p.title(), 60, "职称");
        String unit = text(p.unit(), 120, "单位");
        StringBuilder s = new StringBuilder("<section class=\"slide k-bio\"><div class=\"bio-v\">");
        if (!portrait.isEmpty()) {
            s.append("<figure class=\"photo-card a-in\"><img src=\"").append(portrait).append("\" alt=\"\"></figure>");
        } else {
            s.append("<figure class=\"photo-card a-in photo-empty\"></figure>");
        }
        if (!role.isEmpty()) s.append("<p class=\"role-badge a-in\" data-step><span")
                .append(ed(idx, "persons[0].role")).append(">").append(role).append("</span></p>");
        String sizeClass = name.length() > 9 ? " t-long" : "";
        if (!name.isEmpty()) {
            s.append("<h1 class=\"flow bio-name a-wide").append(sizeClass).append("\"><span")
                    .append(ed(idx, "persons[0].name")).append(">").append(name).append("</span>");
            if (!title.isEmpty()) s.append("<small").append(ed(idx, "persons[0].title")).append(">").append(title).append("</small>");
            s.append("</h1>");
        }
        if (!unit.isEmpty()) s.append("<p class=\"bio-unit a-in\" data-step><span")
                .append(ed(idx, "persons[0].unit")).append(">").append(unit).append("</span></p>");
        return s.append("</div></section>\n").toString();
    }

    private static String coverHtml(int idx, String label, String title, List<String> bodyLines, List<String> items) {
        StringBuilder s = new StringBuilder("<section class=\"slide k-cover\"><div class=\"cover\">");
        s.append("<i class=\"cover-corner tl\"></i><i class=\"cover-corner br\"></i>");
        s.append("<div class=\"cover-emblem a-in\"><i></i></div>");
        if (!label.isEmpty()) s.append("<p class=\"cover-org a-in\"><span").append(ed(idx, "label")).append(">").append(label).append("</span></p>");
        if (!title.isEmpty()) {
            String sizeClass = title.length() > 8 ? " t-long" : "";
            s.append("<h1 class=\"flow cover-title a-wide").append(sizeClass).append("\"").append(ed(idx, "title")).append(">").append(title).append("</h1>");
        }
        if (!bodyLines.isEmpty()) s.append("<p class=\"cover-sub a-in\"><span").append(ed(idx, "body[0]")).append(">").append(bodyLines.get(0)).append("</span></p>");
        s.append("<div class=\"cover-line a-in\"></div>");
        if (items != null) {
            int n = Math.min(items.size(), 12);
            for (int j = 0; j < n; j++) {
                String item = text(items.get(j), 160, "封面信息行");
                if (item.isEmpty()) continue;
                String[] kv = item.split("\\|", 2);
                s.append("<div class=\"ci-row\" data-step");
                if (kv.length == 2 && !kv[0].isBlank()) {
                    s.append("><span class=\"ci-k\"").append(ed(idx, "items[" + j + "].k")).append(">").append(kv[0])
                            .append("</span><span class=\"ci-v\"").append(ed(idx, "items[" + j + "].v")).append(">").append(kv[1]).append("</span></div>");
                } else {
                    s.append("><span class=\"ci-v\"").append(ed(idx, "items[" + j + "]")).append(">").append(item).append("</span></div>");
                }
            }
        }
        return s.append("</div></section>\n").toString();
    }

    /** 章节过场页：编号+流光标题+章节全体出场专家圆头像（多人时横排，单人与原版一致仅头像+姓名）。 */
    private static String sectionHtml(int idx, String label, String title, List<String> items, List<Person> persons) {
        String num = items == null || items.isEmpty() ? "" : text(items.get(0), 12, "章节编号");
        List<Person> cast = persons == null ? List.of() : Arrays.stream(persons.toArray(new Person[0]))
                .filter(p -> p != null && p.name() != null && !p.name().isBlank())
                .limit(6)
                .toList();
        StringBuilder s = new StringBuilder("<section class=\"slide k-section\"><div class=\"sec\">");
        String wm = title.length() > 2 ? title.substring(title.length() - 2) : title;
        if (!wm.isEmpty()) s.append("<div class=\"sec-wm\" aria-hidden=\"true\">").append(wm).append("</div>");
        if (!num.isEmpty()) s.append("<div class=\"sec-num a-in\"").append(ed(idx, "items[0]")).append(">").append(num).append("</div>");
        if (!title.isEmpty()) s.append("<h1 class=\"sec-title flow a-wide\"").append(ed(idx, "title")).append(">").append(title).append("</h1>");
        s.append("<div class=\"sec-line a-in\"></div>");
        if (!cast.isEmpty()) {
            s.append("<div class=\"sec-people a-in\" data-step>");
            for (int j = 0; j < cast.size(); j++) {
                Person p = cast.get(j);
                String name = text(p.name(), 60, "姓名");
                String portrait = image(p.image());
                s.append("<figure class=\"sec-p\"><span class=\"sec-p-avatar\">");
                if (!portrait.isEmpty()) s.append("<img src=\"").append(portrait).append("\" alt=\"\">");
                else s.append("<span class=\"sec-p-word\">").append(name.substring(0, 1)).append("</span>");
                s.append("</span><figcaption><span").append(ed(idx, "persons[" + j + "].name")).append(">")
                        .append(name).append("</span>");
                String title2 = text(p.title(), 60, "职称");
                if (!title2.isEmpty()) s.append("<small>").append(title2).append("</small>");
                s.append("</figcaption></figure>");
            }
            s.append("</div>");
        }
        return s.append("</div></section>\n").toString();
    }

    /** 专家详情页（原简历页）：左栏圆头像+大会角色+流光姓名+单位，右栏简介条目（≤8 条单列，更多两列）。 */
    private static String resumeHtml(int idx, String label, String title, List<String> items, List<Person> persons, Double fontSize) {
        Person p = persons == null ? null : Arrays.stream(persons.toArray(new Person[0]))
                .filter(x -> x != null && x.name() != null && !x.name().isBlank()).findFirst().orElse(null);
        String portrait = p == null ? "" : image(p.image());
        String role = p == null ? "" : text(p.role(), 40, "大会角色");
        String name = p == null ? "" : text(p.name(), 60, "姓名");
        String title2 = p == null ? "" : text(p.title(), 60, "职称");
        String unit = p == null ? "" : text(p.unit(), 120, "单位");
        String fs = fontSize == null ? "" : " style=\"--rfs:" + Math.max(0.7, Math.min(1.5, fontSize)) + "\"";
        StringBuilder s = new StringBuilder("<section class=\"slide k-resume\"").append(fs)
                .append("><div class=\"rp2\">");
        s.append("<aside class=\"rp2-left\">");
        if (!portrait.isEmpty()) s.append("<img class=\"rp2-avatar a-in\" src=\"").append(portrait).append("\" alt=\"\">");
        if (!role.isEmpty()) s.append("<p class=\"role-badge a-in\" data-step><span")
                .append(ed(idx, "persons[0].role")).append(">").append(role).append("</span></p>");
        if (!name.isEmpty()) {
            s.append("<h2 class=\"flow rp2-name a-wide\" data-step><span")
                    .append(ed(idx, "persons[0].name")).append(">").append(name).append("</span>");
            if (!title2.isEmpty()) s.append("<small").append(ed(idx, "persons[0].title")).append(">").append(title2).append("</small>");
            s.append("</h2>");
        }
        if (!unit.isEmpty()) s.append("<p class=\"rp2-unit a-in\" data-step><span")
                .append(ed(idx, "persons[0].unit")).append(">").append(unit).append("</span></p>");
        s.append("</aside><div class=\"rp2-main\">");
        s.append("<h3 class=\"rp2-head a-in\"><span").append(ed(idx, "label")).append(">")
                .append(label.isEmpty() ? "个人简历" : label).append("</span><span>RESUME</span></h3>");
        s.append("<div class=\"rp-rule a-in\"></div><ul class=\"rp2-list")
                .append(items != null && items.size() > 8 ? " two" : "").append("\">");
        if (items != null) {
            int n = Math.min(items.size(), 30);
            for (int j = 0; j < n; j++) {
                String item = text(items.get(j), 200, "简历条目");
                if (!item.isEmpty()) s.append("<li data-step").append(ed(idx, "items[" + j + "]")).append(">").append(item).append("</li>");
            }
        }
        return s.append("</ul></div></div></section>\n").toString();
    }

    private static String panelHtml(int idx, String label, String title, List<Person> persons) {
        StringBuilder s = new StringBuilder("<section class=\"slide k-panel\"><div class=\"panel\">");
        if (!label.isEmpty()) s.append("<p class=\"panel-eyebrow a-in\"><span").append(ed(idx, "label")).append(">").append(label).append("</span></p>");
        if (!title.isEmpty()) s.append("<h1 class=\"flow a-wide\"").append(ed(idx, "title")).append(">").append(title).append("</h1>");
        s.append("<div class=\"rule a-in\"></div><div class=\"panel-cards\">");
        if (persons != null) {
            int n = Math.min(persons.size(), 8);
            for (int j = 0; j < n; j++) {
                Person p = persons.get(j);
                if (p == null || p.name() == null || p.name().isBlank()) continue;
                String name = text(p.name(), 60, "姓名");
                String title2 = text(p.title(), 60, "职称");
                String unit = text(p.unit(), 120, "单位");
                String portrait = image(p.image());
                s.append("<figure class=\"p-card a-in\" data-step>");
                if (!portrait.isEmpty()) {
                    s.append("<img class=\"p-avatar\" src=\"").append(portrait).append("\" alt=\"\">");
                } else {
                    s.append("<span class=\"p-avatar-word\">").append(name.substring(0, 1)).append("</span>");
                }
                s.append("<figcaption><strong><span").append(ed(idx, "persons[" + j + "].name")).append(">").append(name).append("</span>");
                if (!title2.isEmpty()) s.append("<small").append(ed(idx, "persons[" + j + "].title")).append(">").append(title2).append("</small>");
                s.append("</strong>");
                if (!unit.isEmpty()) s.append("<span").append(ed(idx, "persons[" + j + "].unit")).append(">").append(unit).append("</span>");
                s.append("</figcaption></figure>");
            }
        }
        return s.append("</div></div></section>\n").toString();
    }

    private static String legacyContentHtml(int idx, String kind, String label, String title, String portrait,
                                            List<String> lines) {
        String sizeClass = title.length() > 28 ? " t-xlong" : title.length() > 14 ? " t-long" : "";
        StringBuilder s = new StringBuilder("<section class=\"slide k-").append(kind).append("\">");
        switch (kind) {
            case "cover", "ending" -> {
                s.append("<div class=\"wrap center\">");
                if (kind.equals("cover") && !label.isEmpty()) s.append("<p class=\"org a-in\"><span")
                        .append(ed(idx, "label")).append(">").append(label).append("</span></p>");
                if (!title.isEmpty()) s.append("<h1 class=\"flow a-wide").append(sizeClass).append("\"")
                        .append(ed(idx, "title")).append(">").append(title).append("</h1>");
                s.append("<div class=\"rule a-in\"></div>");
                for (int j = 0; j < lines.size(); j++) {
                    s.append("<p class=\"info\" data-step><span").append(ed(idx, "body[" + j + "]")).append(">")
                            .append(lines.get(j)).append("</span></p>");
                }
                s.append("</div>");
            }
            case "bio" -> {
                s.append("<div class=\"wrap bio\"><figure class=\"photo a-in\"><img src=\"").append(portrait)
                        .append("\" alt=\"\"></figure><div class=\"bio-main\">");
                if (!label.isEmpty()) s.append("<p class=\"crumb a-in\"><span").append(ed(idx, "label")).append(">").append(label).append("</span></p>");
                if (!title.isEmpty()) s.append("<h1 class=\"flow").append(sizeClass).append("\"").append(ed(idx, "title")).append(">").append(title).append("</h1>");
                s.append("<div class=\"rule a-in\"></div>");
                if (!lines.isEmpty()) {
                    s.append("<ul class=\"lines\">");
                    for (int j = 0; j < lines.size(); j++) {
                        s.append("<li data-step").append(ed(idx, "body[" + j + "]")).append(">").append(lines.get(j)).append("</li>");
                    }
                    s.append("</ul>");
                }
                s.append("</div></div>");
            }
            default -> {
                s.append("<div class=\"wrap\">");
                if (!label.isEmpty()) s.append("<p class=\"crumb a-in\"><span").append(ed(idx, "label")).append(">").append(label).append("</span></p>");
                if (!title.isEmpty()) s.append("<h1 class=\"flow").append(sizeClass).append("\"").append(ed(idx, "title")).append(">").append(title).append("</h1>");
                s.append("<div class=\"rule a-in\"></div>");
                if (!lines.isEmpty()) {
                    s.append("<ul class=\"lines\">");
                    for (int j = 0; j < lines.size(); j++) {
                        s.append("<li data-step").append(ed(idx, "body[" + j + "]")).append(">").append(lines.get(j)).append("</li>");
                    }
                    s.append("</ul>");
                }
                s.append("</div>");
            }
        }
        return s.append("</section>\n").toString();
    }

    private static String kindOf(String label, String title, String portrait, boolean first) {
        if (label.contains("议程")) return "agenda";
        if (label.contains("感谢") || title.contains("感谢") || label.contains("闭幕") || title.contains("闭幕")) return "ending";
        if (!portrait.isEmpty()) return "bio";
        if (first || label.contains("串场") || label.contains("开场") || label.contains("封面")) return "cover";
        return "content";
    }

    private static String deckTail(Theme theme) {
        String tail = """
                </div><nav id="hud" aria-label="播放控制"><button id="prev">上一页</button>
                <output id="count"></output><span id="prog"><i id="progFill"></i></span>
                <button id="full">全屏</button><button id="next">下一页</button></nav>
                <div id="editTip">编辑模式：直接点击页面文字修改；完成后回向导点「保存修改」</div>
                <script>
                const deck=document.getElementById('deck');
                const slides=Array.from(deck.children);
                const count=document.getElementById('count');
                const progFill=document.getElementById('progFill');
                const tpl=document.getElementById('theme');
                let page=0,timers=[];
                function later(fn,ms){timers.push(setTimeout(fn,ms));}
                function clearTimers(){timers.forEach(clearTimeout);timers=[];}
                function stepsOf(s){return Array.from(s.querySelectorAll('[data-step]'));}
                function hiddenEl(n){return n.classList.contains('ppt-step-hidden');}
                function resetSteps(s){stepsOf(s).forEach(n=>n.classList.add('ppt-step-hidden'));}
                function revealNext(s){const st=stepsOf(s);for(const n of st){if(hiddenEl(n)){n.classList.remove('ppt-step-hidden');return true;}}return false;}
                function revealPrev(s){const st=stepsOf(s);for(let i=st.length-1;i>=0;i--){if(!hiddenEl(st[i])){st[i].classList.add('ppt-step-hidden');return true;}}return false;}
                function autoPlay(s){
                  const pend=stepsOf(s).filter(hiddenEl);if(!pend.length)return;
                  const iv=Math.max(110,Math.min(240,Math.round(1250/Math.max(pend.length,1))));
                  later(function step(){if(revealNext(s))later(step,iv);},420);
                }
                function show(i){
                  clearTimers();
                  const old=slides[page];
                  if(old){old.classList.remove('active');resetSteps(old);}
                  page=Math.max(0,Math.min(slides.length-1,i));
                  const s=slides[page];
                  resetSteps(s);s.classList.add('active');
                  count.textContent=(page+1)+' / '+slides.length;
                  progFill.style.width=((page+1)/slides.length*100)+'%';
                  autoPlay(s);
                  if(editing)ensureFsBar();
                }
                function next(){const s=slides[page];if(revealNext(s))return;show(page+1);}
                function prev(){const s=slides[page];if(revealPrev(s))return;show(page-1);}
                // ===== 所见即所得编辑模式：按 E 或由向导触发；可编辑元素带 data-edit 路径，向导读取写回 =====
                // 简历页额外提供简介字号批量调整（A−/A＋/恢复），修改记录在 slide.dataset.fsChanged 供向导写回
                let editing=false;
                function toggleEdit(){
                  editing=!editing;
                  document.body.classList.toggle('hppt-editing',editing);
                  document.querySelectorAll('[data-edit]').forEach(n=>{
                    if(editing){n.setAttribute('contenteditable','true');}
                    else{n.removeAttribute('contenteditable');n.blur();}
                  });
                  if(editing)ensureFsBar();
                  const tip=document.getElementById('editTip');
                  if(tip)tip.style.display=editing?'block':'none';
                }
                function ensureFsBar(){
                  const s=slides[page];
                  if(!s||!s.classList.contains('k-resume')||s.querySelector('.fs-bar'))return;
                  const bar=document.createElement('div');
                  bar.className='fs-bar';
                  bar.innerHTML='<button data-fsact="down">简介 A−</button><button data-fsact="up">简介 A＋</button><button data-fsact="reset">恢复默认</button>';
                  bar.addEventListener('click',ev=>{
                    const act=ev.target?.dataset?.fsact;
                    if(!act)return;
                    const cur=parseFloat(s.dataset.fsChanged||'1')||1;
                    const v=act==='down'?Math.max(.7,cur-.1):act==='up'?Math.min(1.5,cur+.1):1;
                    s.dataset.fsChanged=v.toFixed(2);
                    s.style.setProperty('--rfs',v.toFixed(2));
                  });
                  s.appendChild(bar);
                }
                window.__toggleEdit=toggleEdit;
                window.__isEditing=()=>editing;
                document.getElementById('prev').onclick=prev;
                document.getElementById('next').onclick=next;
                document.getElementById('full').onclick=()=>document.documentElement.requestFullscreen?.().catch(()=>{});
                document.addEventListener('keydown',e=>{
                  if(e.target instanceof HTMLButtonElement&&(e.key===' '||e.key==='Enter'))return;
                  if(e.target instanceof HTMLElement&&e.target.isContentEditable)return;
                  if((e.key==='e'||e.key==='E')&&!e.ctrlKey&&!e.metaKey&&!e.altKey){e.preventDefault();toggleEdit();return;}
                  if(editing)return;
                  if(['ArrowRight','PageDown',' '].includes(e.key)){e.preventDefault();next();}
                  else if(['ArrowLeft','PageUp'].includes(e.key)){e.preventDefault();prev();}
                  else if(e.key==='Home')show(0);
                  else if(e.key==='End')show(slides.length-1);
                });
                function fit(){const s=Math.min(innerWidth/1920,innerHeight/1080);
                  deck.style.transform='translate(-50%,-50%) scale('+s+')';}
                addEventListener('resize',fit);fit();
                if(tpl){const frag=tpl.content;for(const s of slides){s.prepend(frag.cloneNode(true));}}
                show(0);
                (function(){
                  if(!__PARTICLES__)return;
                  const cv=document.getElementById('fx');if(!cv)return;
                  const ctx=cv.getContext('2d');
                  const rgb=(getComputedStyle(document.body).getPropertyValue('--particle')||'255,255,255').trim();
                  const P=[];
                  function rs(){cv.width=innerWidth;cv.height=innerHeight;}
                  rs();addEventListener('resize',rs);
                  function mk(){return{x:Math.random()*cv.width,y:cv.height+Math.random()*160,
                    r:.8+Math.random()*2.1,s:.22+Math.random()*.7,a:.12+Math.random()*.45,ph:Math.random()*6.283};}
                  for(let i=0;i<80;i++){const p=mk();p.y=Math.random()*cv.height;P.push(p);}
                  (function tick(){
                    ctx.clearRect(0,0,cv.width,cv.height);
                    for(const p of P){
                      p.y-=p.s;p.ph+=.018;
                      if(p.y<-20){Object.assign(p,mk());}
                      const a=p.a*(.55+.45*Math.sin(p.ph*2));
                      ctx.beginPath();
                      ctx.arc(p.x+Math.sin(p.ph)*14,p.y,p.r,0,6.283);
                      ctx.fillStyle='rgba('+rgb+','+a.toFixed(3)+')';
                      ctx.fill();
                    }
                    requestAnimationFrame(tick);
                  })();
                })();
                </script></html>
                """;
        return tail.replace("__PARTICLES__", String.valueOf(theme.particles()));
    }

    private static String text(String value, int limit, String field) {
        if (value == null) return "";
        if (value.length() > limit) {
            throw new BusinessException("「" + field + "」超出长度限制（" + value.length() + "/" + limit
                    + " 字），请缩短后重试；如为简介可按句拆分为多条");
        }
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
