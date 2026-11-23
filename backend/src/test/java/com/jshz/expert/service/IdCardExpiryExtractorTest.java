package com.jshz.expert.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 身份证有效期解析单测（2026-11-23）：覆盖 OCR 常见输出形态与干扰文本。 */
class IdCardExpiryExtractorTest {

    @Test void standardDottedRange() {
        String text = "签发机关\n某某市公安局某某分局\n有效期限\n2015.05.01-2025.05.01";
        assertEquals("2015.05.01-2025.05.01", IdCardExpiryExtractor.parse(text));
    }

    @Test void compactDigitsRange() {
        assertEquals("2015.05.01-2025.05.01",
                IdCardExpiryExtractor.parse("有效期限 20150501-20250501"));
    }

    @Test void chineseDateWithLongTerm() {
        assertEquals("2015.05.01-长期",
                IdCardExpiryExtractor.parse("有效期限\n2015年5月1日-长期"));
    }

    @Test void longTermDotted() {
        assertEquals("2015.05.01-长期",
                IdCardExpiryExtractor.parse("2015.05.01-长期"));
    }

    @Test void singleDigitMonthDayNormalized() {
        assertEquals("2020.01.05-2040.01.05",
                IdCardExpiryExtractor.parse("有效期限 2020.1.5-2040.1.5"));
    }

    @Test void ocrNoiseBeforeKeywordStillParsed() {
        String text = "中华人民共和国\n居民身份证\n签发机关 某某公安局\n有效期限 2018.03.12-2038.03.12\n公民身份号码出现在正面不应干扰";
        assertEquals("2018.03.12-2038.03.12", IdCardExpiryExtractor.parse(text));
    }

    // ==================== 有效期风险（1.0.133） ====================

    @Test void endDateParsedAndLongTermNull() {
        assertEquals(java.time.LocalDate.of(2027, 11, 28), IdCardExpiryExtractor.endDate("2007.11.28-2027.11.28"));
        assertNull(IdCardExpiryExtractor.endDate("2007.11.28-长期"));
        assertNull(IdCardExpiryExtractor.endDate(null));
        assertNull(IdCardExpiryExtractor.endDate(""));
    }

    @Test void riskExpiredAndExpiringSoon() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 23);
        assertEquals("EXPIRED", IdCardExpiryExtractor.risk("2005.12.26-2025.12.26", today));
        assertEquals("EXPIRED", IdCardExpiryExtractor.risk("2006.06.15-2026.06.15", today));
        assertEquals("EXPIRING_SOON", IdCardExpiryExtractor.risk("2006.06.15-2027.03.01", today));
        // 恰好 6 个月边界（含）
        assertEquals("EXPIRING_SOON", IdCardExpiryExtractor.risk("2020.01.01-2027.05.23", today));
        assertNull(IdCardExpiryExtractor.risk("2020.01.01-2027.05.24", today));
        assertNull(IdCardExpiryExtractor.risk("2007.11.28-长期", today));
        assertNull(IdCardExpiryExtractor.risk(null, today));
    }

    // ==================== 正面身份号码（1.0.133） ====================

    @Test void idNumberWithValidChecksumExtracted() {
        // 11010519491231002X 为国标示例号（校验码 X 合法）
        String text = "姓名 张三\n性别 男 民族 汉\n出生 1949年12月31日\n住址 北京市朝阳区\n公民身份号码 11010519491231002X";
        assertEquals("11010519491231002X", IdCardExpiryExtractor.parseIdNumber(text));
    }

    @Test void idNumberChecksumInvalidRejected() {
        // 末位改 1 → 校验不通过，不采纳
        assertNull(IdCardExpiryExtractor.parseIdNumber("公民身份号码 110105194912310021"));
        assertNull(IdCardExpiryExtractor.parseIdNumber(null));
    }

    @Test void checksumValidation() {
        assertTrue(IdCardExpiryExtractor.checksumValid("11010519491231002X"));
        assertFalse(IdCardExpiryExtractor.checksumValid("110105194912310021"));
        assertFalse(IdCardExpiryExtractor.checksumValid("123"));
    }

    @Test void noRangeReturnsNull() {
        assertNull(IdCardExpiryExtractor.parse("签发机关\n某某市公安局某某分局"));
        assertNull(IdCardExpiryExtractor.parse(null));
        assertNull(IdCardExpiryExtractor.parse("  "));
    }
}
