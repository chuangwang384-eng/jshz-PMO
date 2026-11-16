package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.AiAssistantEntry;
import com.jshz.expert.mapper.AiAssistantEntryMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 助手（2026-11-16）：系统使用问答 + 操作指引卡片。
 * 链路：关键词打分取候选 →（可选）本地 Ollama 在候选中做意图选择 → 返回人工维护的条目卡片。
 * 链接/步骤/答案均来自 ai_assistant_entry（管理员维护），模型不生成操作路径、不做任何写操作；
 * Ollama 不可用/超时自动退化为关键词命中，保证基础可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final AiAssistantEntryMapper entryMapper;
    private final OllamaService ollamaService;
    private final ObjectMapper json = new ObjectMapper();

    /** 问答入口：登录即可用；按用户权限过滤条目。 */
    public Map<String, Object> ask(String question, AuthUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        String q = question == null ? "" : question.trim();
        if (q.isEmpty() || q.length() > 200) {
            throw new BusinessException("请输入 1-200 字的问题");
        }
        List<AiAssistantEntry> visible = entryMapper.selectList(new LambdaQueryWrapper<AiAssistantEntry>()
                        .eq(AiAssistantEntry::getIsActive, 1).orderByAsc(AiAssistantEntry::getSort))
                .stream().filter(e -> canSee(e, user)).toList();

        List<Scored> scored = new ArrayList<>();
        for (AiAssistantEntry e : visible) {
            int score = score(q, e);
            if (score > 0) {
                scored.add(new Scored(e, score));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.score).reversed());
        List<Scored> candidates = scored.size() > 5 ? scored.subList(0, 5) : scored;

        if (candidates.isEmpty()) {
            result.put("matched", false);
            result.put("answer", "抱歉，知识库中暂时没有匹配的内容。你可以换个说法再问，或联系系统管理员获取帮助。");
            result.put("mode", "none");
            return result;
        }

        AiAssistantEntry picked = candidates.get(0).entry;
        String mode = "keyword";
        // 候选唯一或头部明显领先时直接命中，省一次模型调用；否则让本地小模型在候选中选择
        boolean dominant = candidates.size() == 1 || candidates.get(0).score >= candidates.get(1).score * 2;
        if (!dominant && ollamaService.available()) {
            String key = llmPick(q, candidates);
            if (key != null) {
                for (Scored s : candidates) {
                    if (s.entry.getEntryKey().equals(key)) {
                        picked = s.entry;
                        mode = "llm";
                        break;
                    }
                }
            }
        }
        result.put("matched", true);
        result.put("mode", mode);
        result.put("entryKey", picked.getEntryKey());
        result.put("title", picked.getTitle());
        result.put("answer", picked.getAnswer());
        result.put("linkUrl", picked.getLinkUrl());
        result.put("linkLabel", picked.getLinkLabel());
        result.put("steps", lines(picked.getSteps()));
        log.info("AI助手问答 matched={} mode={} key={} qLen={}", true, mode, picked.getEntryKey(), q.length());
        return result;
    }

    // ==================== 条目管理（ai:manage） ====================

    public List<AiAssistantEntry> listEntries() {
        return entryMapper.selectList(new LambdaQueryWrapper<AiAssistantEntry>()
                .orderByAsc(AiAssistantEntry::getSort).orderByAsc(AiAssistantEntry::getId));
    }

    @Transactional
    public List<AiAssistantEntry> saveEntries(List<Map<String, Object>> rows, AuthUser user) {
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String key = str(row.get("entryKey"));
            String title = str(row.get("title"));
            String answer = str(row.get("answer"));
            if (key == null || !key.matches("[A-Z0-9_]{1,50}")) {
                throw new BusinessException("entryKey 仅允许大写字母/数字/下划线：" + key);
            }
            if (title == null || answer == null) {
                throw new BusinessException("条目标题与答案不能为空");
            }
            AiAssistantEntry entity = entryMapper.selectOne(new LambdaQueryWrapper<AiAssistantEntry>()
                    .eq(AiAssistantEntry::getEntryKey, key));
            boolean exists = entity != null;
            if (!exists) {
                entity = new AiAssistantEntry();
                entity.setEntryKey(key);
            }
            entity.setTitle(title);
            entity.setQuestions(str(row.get("questions")));
            entity.setAnswer(answer);
            entity.setLinkUrl(str(row.get("linkUrl")));
            entity.setLinkLabel(str(row.get("linkLabel")));
            entity.setSteps(str(row.get("steps")));
            entity.setPermCode(str(row.get("permCode")));
            Object sort = row.get("sort");
            entity.setSort(sort == null ? 0 : Integer.parseInt(String.valueOf(sort)));
            Object active = row.get("isActive");
            entity.setIsActive(active == null || "1".equals(String.valueOf(active)) ? 1 : 0);
            if (exists) {
                entryMapper.updateById(entity);
            } else {
                entryMapper.insert(entity);
            }
        }
        log.info("AI助手条目保存 {} 行 by={}", rows == null ? 0 : rows.size(), user == null ? null : user.getUserId());
        return listEntries();
    }

    // ==================== 内部 ====================

    private boolean canSee(AiAssistantEntry e, AuthUser user) {
        String perm = e.getPermCode();
        if (perm == null || perm.isBlank()) {
            return true;
        }
        return user != null && (user.isAdmin() || user.hasPermission(perm));
    }

    /**
     * 关键词打分：中文按字符二元组（bigram）重合计数，整行问法命中额外加分。
     * 不依赖分词器，短问句场景足够且零依赖。
     */
    private int score(String q, AiAssistantEntry e) {
        String corpus = (e.getTitle() + "\n" + (e.getQuestions() == null ? "" : e.getQuestions())).toLowerCase();
        String ql = q.toLowerCase();
        int score = 0;
        // 整行问法/标题包含关系（强信号）
        for (String line : corpus.split("\n")) {
            String t = line.trim();
            if (t.length() >= 2 && (ql.contains(t) || t.contains(ql))) {
                score += 8;
            }
        }
        // bigram 重合
        Set<String> qgrams = bigrams(ql);
        Set<String> cgrams = bigrams(corpus);
        for (String g : qgrams) {
            if (cgrams.contains(g)) {
                score += 1;
            }
        }
        return score;
    }

    private Set<String> bigrams(String s) {
        Set<String> set = new LinkedHashSet<>();
        String cleaned = s.replaceAll("[\\s，。？！、,.?!；;：:（）()\"'\\-]+", "");
        for (int i = 0; i + 2 <= cleaned.length(); i++) {
            set.add(cleaned.substring(i, i + 2));
        }
        return set;
    }

    /** 让本地小模型在候选条目中选择最匹配的一个；只允许返回候选内的 entryKey，失败返回 null。 */
    private String llmPick(String q, List<Scored> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是公司内部管理系统的客服意图分类器。根据用户问题，从候选条目中选出最匹配的一个。\n");
        sb.append("只输出 JSON：{\"entryKey\":\"候选的键\"}；都不匹配输出 {\"entryKey\":null}。不要输出其他内容。\n\n");
        sb.append("用户问题：").append(q).append("\n\n候选条目：\n");
        for (Scored s : candidates) {
            sb.append("- 键=").append(s.entry.getEntryKey())
                    .append(" 标题=").append(s.entry.getTitle())
                    .append(" 问法样例=").append(firstLines(s.entry.getQuestions(), 3)).append('\n');
        }
        try {
            String raw = ollamaService.completeJson(sb.toString());
            if (raw == null) {
                return null;
            }
            JsonNode node = json.readTree(raw);
            String key = node.path("entryKey").asText(null);
            if (key == null || "null".equals(key)) {
                return null;
            }
            for (Scored s : candidates) {
                if (s.entry.getEntryKey().equals(key)) {
                    return key;
                }
            }
            return null; // 模型返回候选外的键视为无效
        } catch (Exception e) {
            log.warn("AI助手意图选择失败，回退关键词：{}", e.getMessage());
            return null;
        }
    }

    private static String firstLines(String text, int n) {
        if (text == null) {
            return "";
        }
        String[] ls = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, ls.length); i++) {
            if (i > 0) {
                sb.append('；');
            }
            sb.append(ls[i].trim());
        }
        return sb.toString();
    }

    private static List<String> lines(String text) {
        List<String> list = new ArrayList<>();
        if (text != null) {
            for (String l : text.split("\n")) {
                if (!l.isBlank()) {
                    list.add(l.trim());
                }
            }
        }
        return list;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private record Scored(AiAssistantEntry entry, int score) {
    }
}
