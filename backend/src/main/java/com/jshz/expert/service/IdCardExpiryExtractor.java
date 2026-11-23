package com.jshz.expert.service;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 身份证 OCR 规则提取（2026-11-23，1.0.132/1.0.133）。
 * 复用本机 PP-OCRv5 文本识别（KnowledgeDocTextExtractor.ppOcrLines，与合规表格 OCR 同一运行时），
 * 纯规则解析，不调用外部模型。提取失败返回 null，由调用方保留原值、不阻断上传。
 * 反面：有效期限归一为 YYYY.MM.DD-YYYY.MM.DD 或 YYYY.MM.DD-长期；
 * 正面：公民身份号码（18 位，校验码通过才采纳，防 OCR 误识）。
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

    // ==================== 有效期风险（1.0.133） ====================

    /** 解析有效期终止日期；长期/空/非法格式返回 null（长期视为无过期风险）。 */
    static LocalDate endDate(String expiry) {
        if (expiry == null || expiry.isBlank()) return null;
        String[] parts = expiry.split("-");
        String end = parts[parts.length - 1].trim();
        if (end.contains("长期")) return null;
        Matcher m = Pattern.compile("(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})").matcher(end);
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 过期风险：EXPIRED=已过期；EXPIRING_SOON=6 个月内到期（含今天）；无风险/长期/未填写返回 null。 */
    static String risk(String expiry, LocalDate today) {
        LocalDate end = endDate(expiry);
        if (end == null || today == null) return null;
        if (end.isBefore(today)) return "EXPIRED";
        if (!end.isAfter(today.plusMonths(6))) return "EXPIRING_SOON";
        return null;
    }

    // ==================== 正面身份号码（1.0.133） ====================

    private static final Pattern ID_NUMBER = Pattern.compile("\\d{17}[\\dX]");
    /** GB 11643 校验码权重与映射（ISO 7064 MOD 11-2）。 */
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    static String extractIdNumber(BufferedImage frontImage) {
        if (frontImage == null) return null;
        return parseIdNumber(KnowledgeDocTextExtractor.ppOcrLines(frontImage));
    }

    /** 纯文本解析（便于单测）：仅采纳校验码通过的 18 位号码，防 OCR 误识写错证件号。 */
    static String parseIdNumber(String text) {
        if (text == null || text.isBlank()) return null;
        String flat = text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        int anchor = flat.indexOf("身份号码");
        String scope = anchor >= 0 ? flat.substring(anchor) : flat;
        String found = firstValidId(scope);
        if (found == null && anchor >= 0) found = firstValidId(flat);
        return found;
    }

    private static String firstValidId(String scope) {
        Matcher m = ID_NUMBER.matcher(scope);
        while (m.find()) {
            if (checksumValid(m.group())) return m.group();
        }
        return null;
    }

    static boolean checksumValid(String id) {
        if (id == null || id.length() != 18) return false;
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') return false;
            sum += (c - '0') * ID_WEIGHTS[i];
        }
        return ID_CHECK[sum % 11] == id.charAt(17);
    }
}
