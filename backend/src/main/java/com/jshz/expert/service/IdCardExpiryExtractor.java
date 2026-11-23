package com.jshz.expert.service;

import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 身份证反面「有效期限」提取（2026-11-23，1.0.132）。
 * 复用本机 PP-OCRv5 文本识别（KnowledgeDocTextExtractor.ppOcrLines，与合规表格 OCR 同一运行时），
 * 纯规则解析日期区间，不调用外部模型。提取失败返回 null，由调用方保留原值、不阻断上传。
 * 输出归一为 YYYY.MM.DD-YYYY.MM.DD 或 YYYY.MM.DD-长期。
 */
final class IdCardExpiryExtractor {

    /** 日期区间：2015.05.01-2025.05.01 / 20150501-20250501 / 2015年5月1日-2025年5月1日 / …-长期 */
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{4})[.\\-/年]?(\\d{1,2})[.\\-/月]?(\\d{1,2})日?[-–—~至]+(\\d{4}[.\\-/年]?\\d{1,2}[.\\-/月]?\\d{1,2}日?|长期)");

    private IdCardExpiryExtractor() {}

    static String extract(BufferedImage backImage) {
        if (backImage == null) return null;
        return parse(KnowledgeDocTextExtractor.ppOcrLines(backImage));
    }

    /** 纯文本解析（与 OCR 解耦，便于单测）；识别文本为空返回 null。 */
    static String parse(String text) {
        if (text == null || text.isBlank()) return null;
        String flat = text.replaceAll("\\s+", "");
        // 优先从「有效」关键词之后匹配（反面含签发机关等干扰文本）；无关键词时全文兜底
        int anchor = flat.indexOf("有效");
        String scope = anchor >= 0 ? flat.substring(anchor) : flat;
        Matcher m = RANGE.matcher(scope);
        if (!m.find()) {
            if (anchor < 0) return null;
            m = RANGE.matcher(flat);
            if (!m.find()) return null;
        }
        String start = normalize(m.group(1), m.group(2), m.group(3));
        String endRaw = m.group(4);
        String end;
        if (endRaw.contains("长期")) {
            end = "长期";
        } else {
            Matcher em = Pattern.compile("(\\d{4})[.\\-/年]?(\\d{1,2})[.\\-/月]?(\\d{1,2})").matcher(endRaw);
            if (!em.find()) return null;
            end = normalize(em.group(1), em.group(2), em.group(3));
        }
        return start + "-" + end;
    }

    private static String normalize(String y, String mo, String d) {
        return y + "." + String.format("%02d", Integer.parseInt(mo)) + "." + String.format("%02d", Integer.parseInt(d));
    }
}
