package com.jshz.expert.service;

import com.jshz.expert.entity.ComplianceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 合规 LLM 重要事项标记（Owner 2026-11-10）：Ollama 不可用（ollamaService=null/未配置）时
 * 必须零影响回退——草稿保持原 priority、无 remark、不抛异常（解析主流程不受阻的回归断言）。
 */
class ComplianceLlmMarkTest {

    private final ComplianceService service = new ComplianceService(
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null);

    @Test
    void nullOllamaKeepsDraftsUnchanged() {
        ComplianceItem a = new ComplianceItem();
        a.setTitle("缴纳保证金");
        a.setPriority("MEDIUM");
        ComplianceItem b = new ComplianceItem();
        b.setTitle("收集参会名单");
        b.setPriority("MEDIUM");
        assertDoesNotThrow(() -> service.markImportantByLlm(List.of(a, b), null));
        assertEquals("MEDIUM", a.getPriority());
        assertEquals("MEDIUM", b.getPriority());
        assertEquals(null, a.getRemark());
    }

    @Test
    void emptyOrNullDraftsNoop() {
        assertDoesNotThrow(() -> service.markImportantByLlm(List.of(), null));
        assertDoesNotThrow(() -> service.markImportantByLlm(null, null));
    }
}
