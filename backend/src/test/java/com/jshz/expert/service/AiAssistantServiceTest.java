package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.AiAssistantEntry;
import com.jshz.expert.mapper.AiAssistantEntryMapper;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * AI 助手：关键词命中、权限过滤、模型不可用回退、条目校验。
 */
@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock AiAssistantEntryMapper entryMapper;
    @Mock OllamaService ollamaService;
    @InjectMocks AiAssistantService service;

    private final AuthUser normal = new AuthUser(6L, "tester", false, false, Set.of(), Set.of("project:view"), true);
    private final AuthUser admin = new AuthUser(1L, "admin", true, false, Set.of(), Set.of(), true);

    private AiAssistantEntry entry(String key, String title, String questions, String perm) {
        AiAssistantEntry e = new AiAssistantEntry();
        e.setEntryKey(key);
        e.setTitle(title);
        e.setQuestions(questions);
        e.setAnswer("答案-" + key);
        e.setLinkUrl("/project");
        e.setLinkLabel("去项目管理");
        e.setSteps("第一步\n第二步");
        e.setPermCode(perm);
        e.setIsActive(1);
        return e;
    }

    @Test
    void keywordMatchReturnsCardWithLinkAndSteps() {
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry("CREATE_PROJECT", "新建项目", "怎么新建项目\n如何立项", null),
                entry("EXPERT_SEARCH", "专家库查询", "怎么查专家", null)));
        lenient().when(ollamaService.available()).thenReturn(false);

        Map<String, Object> r = service.ask("我想新建一个项目", normal);
        assertThat(r.get("matched")).isEqualTo(true);
        assertThat(r.get("entryKey")).isEqualTo("CREATE_PROJECT");
        assertThat(r.get("linkUrl")).isEqualTo("/project");
        assertThat(r.get("steps")).isEqualTo(List.of("第一步", "第二步"));
    }

    @Test
    void noMatchReturnsFallbackWithoutEntry() {
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry("CREATE_PROJECT", "新建项目", "怎么新建项目", null)));
        Map<String, Object> r = service.ask("量子力学是什么", normal);
        assertThat(r.get("matched")).isEqualTo(false);
        assertThat(r.get("mode")).isEqualTo("none");
    }

    @Test
    void permissionFilteredEntriesInvisibleToNormalUser() {
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry("FEE_CONFIG", "收费标准配置", "收费标准在哪改", "project:manage")));
        Map<String, Object> r = service.ask("收费标准在哪改", normal);
        assertThat(r.get("matched")).isEqualTo(false);
        // 管理员可见
        Map<String, Object> r2 = service.ask("收费标准在哪改", admin);
        assertThat(r2.get("matched")).isEqualTo(true);
        assertThat(r2.get("entryKey")).isEqualTo("FEE_CONFIG");
    }

    @Test
    void llmPickOnlyAcceptsCandidateKeys() {
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry("A_ONE", "日程上传与解析", "日程怎么传", null),
                entry("B_TWO", "参会专家确认", "专家怎么确认", null)));
        when(ollamaService.available()).thenReturn(true);
        // 模型返回候选外的键 → 无效，回退关键词头部
        when(ollamaService.completeJson(any(), anyLong())).thenReturn("{\"entryKey\":\"HACKED\"}");
        Map<String, Object> r = service.ask("日程专家", normal);
        assertThat(r.get("matched")).isEqualTo(true);
        assertThat(r.get("mode")).isEqualTo("keyword");
    }

    @Test
    void llmPickSelectsCandidate() {
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry("A_ONE", "日程上传与解析", "日程怎么传", null),
                entry("B_TWO", "参会专家确认", "专家怎么确认", null)));
        when(ollamaService.available()).thenReturn(true);
        when(ollamaService.completeJson(any(), anyLong())).thenReturn("{\"entryKey\":\"B_TWO\"}");
        Map<String, Object> r = service.ask("日程专家", normal);
        assertThat(r.get("entryKey")).isEqualTo("B_TWO");
        assertThat(r.get("mode")).isEqualTo("llm");
    }

    @Test
    void actionIntentSetsAutoNavigateButHowToDoesNot() {
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry("CREATE_PROJECT", "新建项目", "怎么新建项目", null)));
        lenient().when(ollamaService.available()).thenReturn(false);
        Map<String, Object> action = service.ask("帮我新建项目", normal);
        assertThat(action.get("autoNavigate")).isEqualTo(true);
        Map<String, Object> howto = service.ask("怎么新建项目", normal);
        assertThat(howto.get("autoNavigate")).isEqualTo(false);
    }

    @Test
    void askRejectsEmptyOrTooLongQuestion() {
        assertThatThrownBy(() -> service.ask("  ", normal)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.ask("x".repeat(201), normal)).isInstanceOf(BusinessException.class);
    }

    @Test
    void saveEntriesValidatesKeyAndRequiredFields() {
        assertThatThrownBy(() -> service.saveEntries(
                List.of(Map.of("entryKey", "bad key", "title", "t", "answer", "a")), normal))
                .isInstanceOf(BusinessException.class).hasMessageContaining("entryKey");
        assertThatThrownBy(() -> service.saveEntries(
                List.of(Map.of("entryKey", "OK_KEY", "title", "t")), normal))
                .isInstanceOf(BusinessException.class).hasMessageContaining("答案");
    }
}
