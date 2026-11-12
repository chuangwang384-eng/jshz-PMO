package com.jshz.expert;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ComplianceNotebookService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业合规记事本隔离库验收（Owner 2026-11-10 需求 AC02/AC04/AC05/AC06）。
 * Opt-in only: dedicated 127.0.0.1:13306/acceptance, never a production schema.
 */
@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB", matches = "acceptance")
@SpringBootTest(properties = {"expert.compliance.due-enabled=false", "expert.compliance.v4-reminder-enabled=false"})
class ComplianceNotebookAcceptanceIT {
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:13306/acceptance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        r.add("spring.datasource.username", () -> "root");
        r.add("spring.datasource.password", () -> System.getenv("ACCEPTANCE_PASSWORD"));
        r.add("expert.jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
    }

    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired ComplianceNotebookService notebook;
    @Autowired ComplianceItemMapper itemMapper;

    static final long DOC = 1L;
    AuthUser user(long id, String... perms) { return new AuthUser(id, "acceptance", false, false, Set.of(), Set.of(perms)); }

    @BeforeEach void fixtures() throws Exception {
        try (var c = dataSource.getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:13306/acceptance?"));
            assertEquals("acceptance", db.queryForObject("select database()", String.class));
        }
        for (String table : List.of("compliance_project_todo", "compliance_project_doc_binding", "compliance_item_hide",
                "compliance_item_history", "compliance_item", "knowledge_favorite", "knowledge_document",
                "compliance_execution_reminder", "compliance_execution_event", "compliance_execution_task",
                "compliance_execution", "project_executor", "project_assistant", "project")) {
            db.update("DELETE FROM " + table);
        }
        db.update("INSERT INTO project(id,name,category,creator_id,owner_id,phase_id,status) VALUES(1,'p1','ONLINE',2,2,1,'ACTIVE')");
        db.update("INSERT INTO knowledge_document(id,category,title,status) VALUES(1,'enterprise-compliance','docA',1)");
        item("10", "张三缴纳保证金"); item("11", "签订安全协议"); item("12", "收集参会名单");
    }

    void item(String id, String title) {
        db.update("INSERT INTO compliance_item(id,item_code,document_id,title,is_draft,withdrawn,priority) VALUES(?,?,1,?,0,0,'MEDIUM')",
                Long.parseLong(id), "code-" + id, title);
    }

    ComplianceItem item(long id) { return itemMapper.selectById(id); }

    // AC02：甲乙各自收藏同一文档，甲隐藏乙不受影响；甲可恢复；取消收藏后条目从甲记事本移除
    @Test
    void ac02HideIsolationPerUser() {
        AuthUser jia = user(5), yi = user(6);
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES(1,5),(1,6)");
        assertEquals(1, notebook.myItems(jia, null, "all").size());
        notebook.hide(10L, jia);
        // 甲隐藏视图可见、常规视图不含；乙完全不受影响
        assertEquals(1, notebook.myItems(jia, null, "hidden").size());
        assertTrue(((List<?>) notebook.myItems(jia, null, "all").get(0).get("items"))
                .stream().noneMatch(i -> (long) ((Map<?, ?>) i).get("id") == 10L));
        assertEquals(3, totalItems(yi));
        notebook.unhide(10L, jia);
        assertEquals(3, totalItems(jia));
        // 取消收藏（删除收藏行）→ 记事本为空
        db.update("DELETE FROM knowledge_favorite WHERE user_id=6");
        assertTrue(notebook.myItems(yi, null, "all").isEmpty());
    }

    int totalItems(AuthUser u) {
        return notebook.myItems(u, null, "all").stream().mapToInt(g -> ((List<?>) g.get("items")).size()).sum();
    }

    // AC04/AC05：真实参与人才可推送；非参与者与仅全量查看权被拒；重复推送去重；绑定冲突拒绝；撤下后快照不变（AC06）
    @Test
    void ac04ToAc06PushBindingSnapshot() {
        AuthUser owner = user(2, "knowledge:maintain"), outsider = user(9), viewAll = user(4, "project:view-all");
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES(1,2)");
        // AC05：非参与者被拒（即使持有 project:view-all 全量查看权）
        assertThrows(BusinessException.class, () -> notebook.push(1L, List.of(10L), outsider));
        assertThrows(BusinessException.class, () -> notebook.push(1L, List.of(10L), viewAll));
        // 历史执行人可推送（四类参与关系之一）
        db.update("INSERT INTO project_executor(project_id,user_id) VALUES(1,3)");
        AuthUser executor = user(3);
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES(1,3)");
        Map<String, Object> r1 = notebook.push(1L, List.of(10L, 11L), executor);
        assertEquals(2, r1.get("added"));
        // AC04：重复推送全部跳过
        Map<String, Object> r2 = notebook.push(1L, List.of(10L), executor);
        assertEquals(0, r2.get("added"));
        assertEquals(1, r2.get("skipped"));
        // AC04：绑定冲突——另一文档已绑定后推不同文档被拒
        db.update("INSERT INTO knowledge_document(id,category,title,status) VALUES(2,'enterprise-compliance','docB',1)");
        db.update("INSERT INTO compliance_item(id,item_code,document_id,title,is_draft,withdrawn,priority) VALUES(20,'code-20',2,'docB事项',0,0,'MEDIUM')");
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES(2,3)");
        assertThrows(BusinessException.class, () -> notebook.push(1L, List.of(20L), executor));
        // AC06：撤下后项目待办快照不变、状态不变
        assertEquals(3L, notebook.withdrawPreview(DOC, owner).get("count"));
        notebook.withdraw(DOC, 3, owner);
        var todos = notebook.projectTodos(1L, owner);
        assertEquals(2, todos.size());
        assertTrue(todos.stream().allMatch(t -> "张三缴纳保证金".equals(t.getTitle()) || "签订安全协议".equals(t.getTitle())));
        assertEquals("PENDING", todos.get(0).getStatus());
        // 撤下后记事本不再出现（AC03 部分）
        assertTrue(notebook.myItems(executor, DOC, "all").isEmpty());
        // 撤下后不可再推送该文档事项
        assertThrows(BusinessException.class, () -> notebook.push(1L, List.of(12L), executor));
        // 撤下计数冲突：expectedCount 不符
        assertThrows(BusinessException.class, () -> notebook.withdraw(DOC, 3, owner));
        db.update("DELETE FROM knowledge_document WHERE id=?", DOC);
        var afterDelete = notebook.projectTodos(1L, owner);
        assertEquals(2, afterDelete.size());
        assertTrue(afterDelete.stream().allMatch(t -> Boolean.TRUE.equals(t.getSourceDeleted())));
        assertEquals(todos.stream().map(t -> t.getTitle()).toList(), afterDelete.stream().map(t -> t.getTitle()).toList());
    }
}
