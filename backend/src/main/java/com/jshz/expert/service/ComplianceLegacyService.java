package com.jshz.expert.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Explicit personal progress and assignments only; no automatic conversion of favorites. */
@Service @RequiredArgsConstructor
public class ComplianceLegacyService {
    private final JdbcTemplate db;
    private final com.jshz.expert.mapper.ComplianceUserModeMapper modes;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public List<Map<String,Object>> history(Long uid) {
        var mode = modes.selectById(uid);
        List<Map<String,Object>> rows;
        if (mode != null && mode.getLegacySnapshot() != null) {
            try { rows = json.readValue(mode.getLegacySnapshot(), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() {}); }
            catch (Exception bad) { throw new IllegalStateException("Legacy snapshot unavailable", bad); }
        } else rows = liveRows(uid);
        Map<Long,Boolean> readable = new HashMap<>();
        for (Map<String,Object> row : rows) {
            Long id = ((Number)row.get("document_id")).longValue();
            boolean allowed = readable.computeIfAbsent(id, key -> db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE id=? AND status=1 AND category='enterprise-compliance'",Integer.class,key) == 1);
            if (!allowed) {
                row.put("title", "来源不可访问的历史事项"); row.put("content", null); row.put("document_title", null);
                row.put("remark", null); row.put("block_reason", null);
            }
        }
        return rows;
    }
    public String capture(Long uid) {
        try { return json.writeValueAsString(liveRows(uid)); }
        catch (Exception bad) { throw new IllegalStateException("Cannot capture legacy snapshot",bad); }
    }
    private List<Map<String,Object>> liveRows(Long uid) {
        List<Map<String,Object>> rows = db.queryForList("""
            SELECT i.id, i.title, i.content, i.document_id, d.title AS document_title,
                   d.status AS document_status, d.category, i.due_date, i.priority,
                   COALESCE(p.progress_status, 'UNPROCESSED') AS progress_status,
                   p.remark, p.block_reason
            FROM compliance_item i
            LEFT JOIN compliance_user_progress p ON p.item_id=i.id AND p.user_id=?
            LEFT JOIN knowledge_document d ON d.id=i.document_id
            WHERE p.user_id IS NOT NULL OR EXISTS
              (SELECT 1 FROM compliance_item_assignee a WHERE a.item_id=i.id AND a.user_id=?)
            ORDER BY i.id DESC
            """, uid, uid);
        return rows;
    }
}
