package com.jshz.expert.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test void noRangeReturnsNull() {
        assertNull(IdCardExpiryExtractor.parse("签发机关\n某某市公安局某某分局"));
        assertNull(IdCardExpiryExtractor.parse(null));
        assertNull(IdCardExpiryExtractor.parse("  "));
    }
}
