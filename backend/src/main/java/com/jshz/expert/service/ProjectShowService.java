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

    public record Slide(String label, String title, String body, String image) {}

    /**
     * deck.template 取 {@link #THEMES} 中的 id，缺失或未知时回退 gold；fontScale 允许范围 0.8~1.4，
     * 缺失时按 1.0。旧草稿 JSON 无这两个字段，反序列化为 null 后由 render 兜底，勿在读取处直接拆箱。
     */
    public record Deck(String background, List<Slide> slides, String template, Double fontScale) {}

    /** 内置串场模板。css 只允许覆盖调色变量与装饰层，页面结构与动画引擎由 render 的公共层承载，禁止复制私有副本。 */
    public record Theme(String id, String name, String css, boolean particles) {}

    private static final Theme GOLD = new Theme("gold", "红金盛典", """
            :root{--bg:radial-gradient(1500px 1000px at 50% 42%,#7e1414 0%,#5d0e0e 46%,#3c0707 100%);
            --deco:radial-gradient(520px 520px at -140px -160px,rgba(255,196,110,.2),transparent 68%),
            radial-gradient(640px 640px at calc(100% + 200px) calc(100% + 220px),rgba(255,150,80,.16),transparent 68%),
            linear-gradient(90deg,transparent,rgba(255,214,140,.06) 45%,rgba(255,236,190,.1) 50%,rgba(255,214,140,.06) 55%,transparent);
            --flow:linear-gradient(100deg,#8a5a1e,#f2c66d 22%,#fff4d6 38%,#f7d98c 50%,#caa050 72%,#8a5a1e);
            --ink:#fff3dc;--ink-dim:#f3d9ac;--accent:#f2c66d;--rule:rgba(242,198,109,.5);
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
            --ink:#eaf4ff;--ink-dim:#9fc6e8;--accent:#58b7ff;--rule:rgba(88,183,255,.45);
            --font-title:"Microsoft YaHei UI","PingFang SC",sans-serif;--font-body:"Microsoft YaHei UI","PingFang SC",sans-serif;
            --particle:120,200,255}
            """, true);

    private static final Theme PAPER = new Theme("paper", "商务简白", """
            :root{--bg:linear-gradient(180deg,#f7f8fb,#eef1f6);
            --deco:linear-gradient(180deg,#254a8f 0 4px,transparent 4px),
            radial-gradient(700px 420px at 88% 96%,rgba(37,99,199,.08),transparent 70%),
            radial-gradient(500px 320px at 6% 8%,rgba(201,166,74,.08),transparent 70%);
            --flow:linear-gradient(100deg,#1f3d7a,#2563c7 25%,#8db9ff 50%,#2563c7 75%,#1f3d7a);
            --ink:#1f2329;--ink-dim:#5f6673;--accent:#2563c7;--rule:rgba(31,61,122,.35);
            --font-title:"Microsoft YaHei UI","PingFang SC",sans-serif;--font-body:"Microsoft YaHei UI","PingFang SC",sans-serif;
            --particle:37,99,199}
            """, false);

    private static final Theme BAMBOO = new Theme("bamboo", "青竹学术", """
            :root{--bg:radial-gradient(1300px 850px at 50% 36%,#f2f9f4 0%,#e2f0e7 55%,#d3e7db 100%);
            --deco:linear-gradient(180deg,#2f8f6b 0 4px,transparent 4px),
            radial-gradient(620px 380px at 90% 12%,rgba(47,143,107,.1),transparent 70%),
            radial-gradient(560px 360px at 8% 92%,rgba(47,143,107,.09),transparent 70%);
            --flow:linear-gradient(100deg,#1d5c46,#2f8f6b 25%,#b9ecd2 50%,#2f8f6b 75%,#1d5c46);
            --ink:#17352a;--ink-dim:#52705f;--accent:#2f8f6b;--rule:rgba(29,92,70,.35);
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
        StringBuilder html = new StringBuilder(head(theme, fs, background));
        for (int i = 0; i < deck.slides().size(); i++) {
            Slide slide = deck.slides().get(i);
            if (slide == null) throw new BusinessException("幻灯片内容无效");
            html.append(slideHtml(slide, i == 0));
            if (html.length() > MAX_DECK_SIZE) throw new BusinessException("串场文件过大，请压缩图片");
        }
        return html.append(deckTail(theme)).toString();
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
            .bio{flex-direction:row;align-items:center;gap:5%}
            .photo{flex:none;width:26%;aspect-ratio:3/4;margin:0;position:relative;overflow:hidden;
              border:2px solid var(--rule);border-radius:14px;
              box-shadow:0 18px 48px rgba(0,0,0,.35)}
            .photo img{width:100%;height:100%;object-fit:cover;object-position:center top;display:block}
            .photo::after{content:"";position:absolute;top:-60%;left:0;width:34%;height:220%;
              transform:translateX(-180%) rotate(22deg);pointer-events:none;
              background:linear-gradient(90deg,transparent,rgba(255,255,255,.22),transparent);
              animation:sweep 5.5s ease-in-out infinite}
            .bio-main{flex:1;min-width:0}
            .bio-main .crumb{margin-bottom:1em}
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

    private static String slideHtml(Slide slide, boolean first) {
        String label = text(slide.label(), 160);
        String title = text(slide.title(), 240);
        String body = text(slide.body(), 4000);
        String portrait = image(slide.image());
        String kind = kindOf(label, title, portrait, first);
        List<String> lines = body.isBlank() ? List.of() : Arrays.stream(body.split("\n"))
                .map(String::strip).filter(line -> !line.isEmpty()).toList();
        String sizeClass = title.length() > 28 ? " t-xlong" : title.length() > 14 ? " t-long" : "";
        StringBuilder s = new StringBuilder("<section class=\"slide k-").append(kind).append("\">");
        switch (kind) {
            case "cover", "ending" -> {
                s.append("<div class=\"wrap center\">");
                if (kind.equals("cover") && !label.isEmpty()) s.append("<p class=\"org a-in\">").append(label).append("</p>");
                if (!title.isEmpty()) s.append("<h1 class=\"flow a-wide").append(sizeClass).append("\">").append(title).append("</h1>");
                s.append("<div class=\"rule a-in\"></div>");
                lines.forEach(line -> s.append("<p class=\"info\" data-step>").append(line).append("</p>"));
                s.append("</div>");
            }
            case "bio" -> {
                s.append("<div class=\"wrap bio\"><figure class=\"photo a-in\"><img src=\"").append(portrait)
                        .append("\" alt=\"\"></figure><div class=\"bio-main\">");
                if (!label.isEmpty()) s.append("<p class=\"crumb a-in\">").append(label).append("</p>");
                if (!title.isEmpty()) s.append("<h1 class=\"flow").append(sizeClass).append("\">").append(title).append("</h1>");
                s.append("<div class=\"rule a-in\"></div>");
                if (!lines.isEmpty()) {
                    s.append("<ul class=\"lines\">");
                    lines.forEach(line -> s.append("<li data-step>").append(line).append("</li>"));
                    s.append("</ul>");
                }
                s.append("</div></div>");
            }
            default -> {
                s.append("<div class=\"wrap\">");
                if (!label.isEmpty()) s.append("<p class=\"crumb a-in\">").append(label).append("</p>");
                if (!title.isEmpty()) s.append("<h1 class=\"flow").append(sizeClass).append("\">").append(title).append("</h1>");
                s.append("<div class=\"rule a-in\"></div>");
                if (!lines.isEmpty()) {
                    s.append("<ul class=\"lines\">");
                    lines.forEach(line -> s.append("<li data-step>").append(line).append("</li>"));
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
                }
                function next(){const s=slides[page];if(revealNext(s))return;show(page+1);}
                function prev(){const s=slides[page];if(revealPrev(s))return;show(page-1);}
                document.getElementById('prev').onclick=prev;
                document.getElementById('next').onclick=next;
                document.getElementById('full').onclick=()=>document.documentElement.requestFullscreen?.().catch(()=>{});
                document.addEventListener('keydown',e=>{
                  if(e.target instanceof HTMLButtonElement&&(e.key===' '||e.key==='Enter'))return;
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
