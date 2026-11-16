package com.jshz.expert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Disposable loopback instance only. All fixture data is rolled back, no production access. */
@EnabledIfEnvironmentVariable(named="DASHBOARD_ACCEPTANCE_DB",matches="dashboard_acceptance")
class DashboardReadIntegrationTest {
    SingleConnectionDataSource source;
    JdbcTemplate db;
    ProjectConfigurationService config;
    DashboardReadService reads;
    PermissionService permissions;
    AuthUser employee=new AuthUser(501L,"fixture-a",false,false,Set.of(),Set.of());
    AuthUser admin=new AuthUser(501L,"fixture-a",true,false,Set.of(),Set.of("project:view-all","approval:audit"));
    @BeforeEach void setup() throws Exception {
        source=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13319/dashboard_acceptance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","root",System.getenv("DASHBOARD_DB_PASSWORD"),true);
        source.setAutoCommit(false);db=new JdbcTemplate(source);
        assertEquals("dashboard_acceptance",db.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(13319,db.queryForObject("SELECT @@port",Integer.class));
        assertTrue(db.queryForObject("SELECT @@datadir",String.class).contains(".build-dashboard-acceptance"));
        // Isolate assertions from synthetic browser fixtures; rollback restores them afterwards.
        for(String table:List.of("project_phase_history","project_executor","project_assistant","project_config_snapshot","project","knowledge_favorite","knowledge_document","expert_info","application","announcement_read","announcement"))
            db.update("DELETE FROM "+table);
        config=new ProjectConfigurationService(db,new ObjectMapper());permissions=mock(PermissionService.class);
        when(permissions.getPermissionRules()).thenReturn(List.of());
        reads=new DashboardReadService(db,config,permissions,new GlobalSearchModules());
    }
    @AfterEach void rollback() throws Exception {if(source!=null){source.getConnection().rollback();source.destroy();}}
    @SuppressWarnings("unchecked") Map<String,Object> data(Map<String,Object> section){return (Map<String,Object>)section.get("data");}
    @SuppressWarnings("unchecked") List<Map<String,Object>> items(Map<String,Object> result){return (List<Map<String,Object>>)result.get("items");}
    void project(long id,long owner,int offset,long phase,String origin,String status) {
        db.update("INSERT INTO project(id,name,category,creator_id,owner_id,phase_id,status,end_date) VALUES (?,?, 'OFFLINE',501,?,?,?,?)",id,"隔离项目"+id,owner,phase,status,LocalDate.now(DashboardReadService.ZONE).plusDays(offset));
        Map<String,Object> snapshot=Map.of("phases",List.of(Map.of("phaseId",10,"name","立项","position",1),Map.of("phaseId",60,"name","已完成","position",2)));
        db.update("INSERT INTO project_config_snapshot(project_id,schema_version,effective_config,config_hash,origin) VALUES (?,1,?,?,?)",id,config.encode(snapshot),config.configHash(snapshot),origin);
    }
    @Test void personalProjectsCoverMembersAndRankByEndDateRisk() {
        project(1001,501,-1,10,"LEGACY_BASELINE","ACTIVE");
        project(1002,999,0,10,"LEGACY_BASELINE","ACTIVE");db.update("INSERT INTO project_executor(project_id,user_id) VALUES (1002,501)");
        project(1003,501,7,10,"LEGACY_BASELINE","ACTIVE");db.update("INSERT INTO project_executor(project_id,user_id) VALUES (1003,501)");
        project(1004,501,-3,60,"LEGACY_BASELINE","ACTIVE");
        project(1005,501,-3,10,"LEGACY_BASELINE","CANCELED");project(1006,501,-3,10,"LEGACY_BASELINE","DELETED");
        project(1007,501,-9,60,"CREATED","ACTIVE");project(1008,999,-1,10,"LEGACY_BASELINE","ACTIVE");
        var result=reads.projects(admin);var list=items(data(result.get("myProjects")));
        // 2026-11-09 流程重塑后：不再按历史「已完成」阶段排除项目，也不再产生 UNKNOWN；CANCELED/DELETED 仍隐藏
        assertEquals(List.of(1007L,1004L,1001L,1008L,1002L,1003L),list.stream().map(m->((Number)m.get("id")).longValue()).toList());
        assertEquals(List.of("负责人","创建人","执行人"),list.get(5).get("myRelations"));
        var risks=data(result.get("projectRisk"));assertEquals(4L,risks.get("overdueCount"));assertEquals(1L,risks.get("dueTodayCount"));assertEquals(1L,risks.get("dueSoonCount"));assertEquals(0L,risks.get("unknownCompletionCount"));
        // 创建人=501 使普通员工同样可见 6 条；管理员不额外扩展
        assertEquals(6,items(data(reads.projects(employee).get("myProjects"))).size());
        var search=reads.search(employee,"project","隔离项目",1,20);assertEquals(7L,search.get("total"));
        assertEquals(7L,reads.search(admin,"project","隔离项目",1,20).get("total"));
        assertFalse(items(search).toString().contains("effective_config"));
        assertEquals("未设置阶段",items(search).get(0).get("extra"));
        assertEquals(1L,reads.search(employee,"project","1001",1,5).get("total"));
    }
    @Test void literalSearchIsStableCaseInsensitiveAndSensitiveFieldsNeverReturn() {
        db.update("INSERT INTO expert_info(id,name,unit,id_card,bank_card) VALUES (201,?,'安全单位','sensitive-id','sensitive-bank'),(202,'abcZZliteral','安全单位',NULL,NULL),(203,'ABC','其他单位',NULL,NULL)","AbC%_\\literal");
        var exact=reads.search(employee,"expert","abc%_\\literal",1,5);
        assertEquals(1L,exact.get("total"));assertEquals(201L,((Number)items(exact).get(0).get("id")).longValue());
        assertFalse(exact.toString().contains("sensitive"));
        var ranked=reads.search(employee,"expert","abc",1,1);assertEquals(3L,ranked.get("total"));assertEquals(203L,((Number)items(ranked).get(0).get("id")).longValue());
        assertEquals(1,items(reads.search(employee,"expert","abc",3,1)).size());
        assertEquals(0L,reads.search(employee,"expert","' OR 1=1 --",1,5).get("total"));
    }
    @Test void knowledgeFavoritesHonorShelfStateAndRestoreWithoutDeletingRelations() {
        db.update("INSERT INTO knowledge_document(id,category,title,file_name,file_path,file_type,status) VALUES (301,'meeting-execution','测试知识','meta.docx','fixture.docx','docx',1),(302,'meeting-execution','测试下架','off.docx','off.docx','docx',0)");
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES (301,501),(302,501),(301,502)");
        assertEquals(1L,data(reads.favorites(employee)).get("total"));
        assertEquals(1L,reads.search(employee,"knowledge","测试",1,5).get("total"));
        db.update("UPDATE knowledge_document SET status=1 WHERE id=302");assertEquals(2L,data(reads.favorites(employee)).get("total"));
        db.update("DELETE FROM knowledge_document WHERE id=301");assertEquals(1L,data(reads.favorites(employee)).get("total"));
        assertEquals(3L,db.queryForObject("SELECT COUNT(*) FROM knowledge_favorite",Long.class));
    }
    @Test void approvalsRequireBothPendingVisibilityAndCurrentAssignee() {
        db.update("INSERT INTO application(id,applicant_id,applicant,application_type,status,approver_snapshot,current_assignee,extra_approver) VALUES (401,502,'other','EXPERT_CREATE',0,NULL,NULL,NULL),(402,502,'other','EXPERT_CREATE',0,NULL,'elsewhere',NULL),(403,502,'other','EXPERT_CREATE',0,NULL,'elsewhere','fixture-a'),(404,501,'fixture-a','EXPERT_CREATE',4,NULL,NULL,NULL),(405,502,'other','EXPERT_CREATE',0,'[{\"username\":\"elsewhere\"}]',NULL,NULL)");
        assertEquals(2L,data(reads.approvals(admin)).get("total"));
        assertThrows(RuntimeException.class,()->reads.approvals(employee));
        assertEquals(1L,data(reads.applications(employee)).get("returnedCount"));
        assertEquals(1,items(Map.of("items",data(reads.applications(employee)).get("latestItems"))).size());
        db.update("UPDATE application SET current_assignee='elsewhere' WHERE id=401");assertEquals(1L,data(reads.approvals(admin)).get("total"));
    }
    @Test void announcementsEnforceEffectiveWindowEvenForManagerAndReadDoesNotChangeOnExposure() {
        LocalDateTime now=LocalDateTime.now(DashboardReadService.ZONE);
        db.update("INSERT INTO announcement(id,title,content,status,effective_time,expire_time) VALUES (601,'测试公告','正文','ONLINE',?,NULL),(602,'未来公告','正文','ONLINE',?,NULL),(603,'到期公告','正文','ONLINE',?,?),(604,'下架公告','正文','OFFLINE',?,NULL)",now.minusDays(1),now.plusDays(1),now.minusDays(2),now.minusSeconds(1),now.minusDays(1));
        assertEquals(1L,data(reads.announcements(admin)).get("total"));
        assertEquals(1L,reads.search(admin,"announcement","公告",1,5).get("total"));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM announcement_read",Long.class));
    }
    @Test void dynamicModulePermissionAppliesBeforeQueryAndCount() {
        when(permissions.getPermissionRules()).thenReturn(List.of(new PermissionService.PermissionRule("GET","/api/experts","expert:blocked")));
        assertFalse(reads.allowed(employee,"expert"));
        assertThrows(RuntimeException.class,()->reads.search(employee,"expert","a",1,5));
    }
}
