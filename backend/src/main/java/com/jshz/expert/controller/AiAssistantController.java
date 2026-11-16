package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.entity.AiAssistantEntry;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 助手（2026-11-16）。
 * /ask 不注册 permission 规则=登录即可用（DbPermissionFilter 默认口径）；
 * 条目管理 GET/PUT /entries 绑定 ai:manage（permission 表数据驱动）。
 */
@RestController
@RequestMapping("/api/ai-assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService assistantService;

    /** 问答：返回匹配的操作指引卡片（答案+跳转链接+步骤）。 */
    @PostMapping("/ask")
    public ApiResponse<Map<String, Object>> ask(@RequestBody Map<String, Object> body,
                                                @AuthenticationPrincipal AuthUser user) {
        String question = body == null || body.get("question") == null ? null : String.valueOf(body.get("question"));
        return ApiResponse.ok(assistantService.ask(question, user));
    }

    @GetMapping("/entries")
    public ApiResponse<List<AiAssistantEntry>> listEntries() {
        return ApiResponse.ok(assistantService.listEntries());
    }

    @PutMapping("/entries")
    public ApiResponse<List<AiAssistantEntry>> saveEntries(@RequestBody Map<String, Object> body,
                                                           @AuthenticationPrincipal AuthUser user) {
        Object rows = body == null ? null : body.get("rows");
        List<Map<String, Object>> list = rows instanceof List<?> l
                ? l.stream().filter(o -> o instanceof Map).map(o -> (Map<String, Object>) o).toList()
                : List.of();
        return ApiResponse.ok(assistantService.saveEntries(list, user));
    }
}
