package com.jshz.expert.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

/**
 * 富文本 XSS 白名单清洗工具。
 * 保留 p/br/strong/em/u/s/h1-h4/ul/ol/li/img/a 等安全标签及基础属性，
 * 自动移除 script、iframe、on* 事件属性等危险内容（jsoup Safelist 机制）。
 */
public final class XssCleanUtil {

    private static final Safelist SAFE_LIST = Safelist.none()
            .addTags("p", "br", "strong", "em", "u", "s",
                    "h1", "h2", "h3", "h4", "ul", "ol", "li",
                    "img", "a", "span", "blockquote", "pre", "code",
                    "hr", "table", "thead", "tbody", "tr", "td", "th")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("a", "href", "target", "rel")
            .addAttributes("span", "style")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("a", "href", "http", "https", "mailto");

    private XssCleanUtil() {
    }

    /**
     * 清洗富文本 HTML：仅保留白名单内标签与属性，返回可安全入库的正文。
     *
     * @param html 原始富文本（wangEditor 输出）
     * @return 清洗后的 HTML（空串入参返回空串）
     */
    public static String clean(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Document.OutputSettings settings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, "", SAFE_LIST, settings);
    }
}
