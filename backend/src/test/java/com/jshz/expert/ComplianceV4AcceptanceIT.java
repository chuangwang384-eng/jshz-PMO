package com.jshz.expert;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.scheduler.ComplianceDueScheduler;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Never connects to production; runs only against this fixed disposable MySQL endpoint. */
@EnabledIfEnvironmentVariable(named="V4_ACCEPTANCE_DB", matches="acceptance_v4")
@SpringBootTest(properties={"expert.compliance.v4-enabled=true", "expert.compliance.v4-reminder-enabled=false", "expert.compliance.due-enabled=false", "expert.wecom.sync-cron-enabled=false", "expert.wecom.corp-id=", "expert.wecom.contact-secret="})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceV4AcceptanceIT {
    @DynamicPropertySource static void isolation(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:13316/acceptance_v4?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        r.add("spring.datasource.username", () -> "root");
        r.add("spring.datasource.password", () -> System.getenv("V4_DB_PASSWORD"));
        r.add("expert.jwt.secret", () -> UUID.randomUUID().toString()+UUID.randomUUID());
        for (String key : List.of("photo.storage-dir", "photo.temp-dir", "knowledge.storage-dir", "knowledge.preview-dir", "customer.storage-dir", "supplier.storage-dir", "project.storage-dir"))
            r.add("expert."+key, () -> Path.of(System.getProperty("java.io.tmpdir"), "jshz-v4-acceptance", key).toString());
    }
    @Autowired JdbcTemplate db;
    @Autowired ComplianceV4Service v4;
    @Autowired ComplianceService old;
    @Autowired ComplianceRolloutService rollout;
    @Autowired ComplianceLegacyService legacy;
    @Autowired ComplianceV4ReminderService delivery;
    @Autowired ComplianceDueScheduler v3scheduler;
    @Autowired PermissionService permissions;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @SpyBean NotificationService notifications;
    AuthUser owner() { return new AuthUser(2L,"验收员工甲",false,false,Set.of(),Set.of()); }
    AuthUser reviewer() { return new AuthUser(1L,"验收维护人",false,false,Set.of(),Set.of("knowledge:maintain")); }
    AuthUser other() { return new AuthUser(3L,"验收员工乙",false,false,Set.of(),Set.of()); }
    @BeforeEach void seed() {
        assertEquals("acceptance_v4", db.queryForObject("SELECT DATABASE()",String.class));
        reset(notifications); permissions.clearCaches();
        for (String t : List.of("notification", "compliance_execution_reminder", "compliance_execution_event", "compliance_execution_task", "compliance_execution", "compliance_template_option", "compliance_template_node", "compliance_template_version", "compliance_user_mode", "compliance_reminder_log", "compliance_user_progress", "compliance_item_assignee", "compliance_item", "knowledge_favorite", "knowledge_doc_version", "knowledge_document", "project_executor", "project", "user_role", "role_permission", "role", "permission", "sys_user")) db.update("DELETE FROM "+t);
        String pass = encoder.encode(System.getenv("V4_TEST_PASSWORD"));
        for (int i=1;i<=3;i++) db.update("INSERT INTO sys_user(id,username,password,is_admin,must_change_password,status) VALUES(?,?,?,0,0,1)", i, i==1?"验收维护人":i==2?"验收员工甲":"验收员工乙",pass);
        db.update("INSERT INTO role(id,code,name) VALUES(1,'FIXTURE','fixture')");
        db.update("INSERT INTO permission(code,name,type) VALUES('knowledge:maintain','fixture',1)");
        db.update("INSERT INTO role_permission(role_id,permission_code) VALUES(1,'knowledge:maintain')");
        db.update("INSERT INTO user_role(user_id,role_id) VALUES(1,1)");
        db.update("INSERT INTO knowledge_document(id,category,title,upload_by,status,content) VALUES(1,'enterprise-compliance','隔离规范','验收员工甲',1,'隔离示例条款')");
        db.update("INSERT INTO project(id,name,category,creator_id,owner_id,phase_id,status) VALUES(1,'隔离项目','ONLINE',2,2,1,'ACTIVE')");
        db.update("INSERT INTO compliance_item(id,item_code,document_id,title,is_draft,due_date) VALUES(1,'FIXTURE-1',1,'历史事项',0,CURRENT_DATE)");
        db.update("INSERT INTO knowledge_favorite(user_id,doc_id) VALUES(2,1)");
        db.update("INSERT INTO compliance_user_progress(user_id,item_id,progress_status,remark) VALUES(2,1,'COMPLETED','历史私人备注')");
    }
    long template() {
        long id=((Number)v4.createDraft(1L,null,"隔离模板",owner()).get("id")).longValue();
        v4.patchTemplate(id,Map.of("rowVersion",0,"nodes",List.of(Map.of("nodeCode","association","title","协会选择","inputType","SINGLE","required",1,"options",List.of(Map.of("optionCode","A","label","协会 A"),Map.of("optionCode","B","label","协会 B"))))),owner());
        v4.publish(id,reviewer()); return id;
    }
    long execution(long template, String key) {
        rollout.enable(2L);
        return ((Number)v4.createExecution(Map.of("templateVersionId",template,"projectId",1,"name","六月独立清单","idempotencyKey",key),owner()).get("executionId")).longValue();
    }
    long task(long execution) { return db.queryForObject("SELECT id FROM compliance_execution_task WHERE execution_id=?",Long.class,execution); }
    int n(String t) { return db.queryForObject("SELECT COUNT(*) FROM "+t,Integer.class); }
    long dueNode(long e) {
        long t=task(e); v4.startExecution(e,Map.of(),owner());
        db.update("INSERT INTO compliance_execution_reminder(execution_id,task_id,recipient_user_id,kind,scheduled_at,schedule_revision) VALUES(?,?,2,'due_today',?,0)",e,t,LocalDateTime.now().minusMinutes(1));
        return db.queryForObject("SELECT MAX(id) FROM compliance_execution_reminder",Long.class);
    }
    @Test void templatePermissionsAndPublishing() {
        assertThrows(BusinessException.class,()->v4.createDraft(1L,null,"denied",other()));
        long id=((Number)v4.createDraft(1L,null,"draft",owner()).get("id")).longValue();
        assertThrows(BusinessException.class,()->v4.templateDetail(id,other()));
        assertThrows(BusinessException.class,()->v4.publish(id,owner()));
        long published=template(); assertEquals("PUBLISHED",v4.templateDetail(published,owner()).get("state"));
        assertThrows(BusinessException.class,()->v4.patchTemplate(published,Map.of("title","overwrite"),owner()));
    }
    @Test void reusableSnapshotsPersonalEditsAndCompletedFiltering() {
        long e=execution(template(),"first"), t=task(e);
        v4.patchTask(t,Map.of("answer",Map.of("optionCode","A"),"note","六月私人备注","dueDate",LocalDate.now().plusDays(7).toString(),"progress","COMPLETED","rowVersion",0),owner());
        v4.startExecution(e,Map.of(),owner());
        assertEquals(0L,((Number)v4.myTasks(owner(),"ACTIVE",null,null,null,null,1,50).get("total")).longValue());
        assertEquals(1L,((Number)v4.myTasks(owner(),"COMPLETED",null,null,null,null,1,50).get("total")).longValue());
        long next=((Number)v4.reuseExecution(e,Map.of("name","十月独立清单","copyAnswers",false,"idempotencyKey","second"),owner()).get("executionId")).longValue();
        Map<String,Object> row=db.queryForMap("SELECT progress,due_date,note,answer FROM compliance_execution_task WHERE execution_id=?",next);
        assertEquals("UNPROCESSED",row.get("progress")); assertNull(row.get("due_date")); assertNull(row.get("note")); assertNull(row.get("answer"));
        assertThrows(BusinessException.class,()->v4.executionDetail(e,other()));
        assertThrows(BusinessException.class,()->v4.taskEvents(t,reviewer()));
        assertEquals("六月私人备注",db.queryForObject("SELECT note FROM compliance_execution_task WHERE id=?",String.class,t));
    }
    @Test void deleteSourceAndProjectRevocationNeverLeakSnapshots() {
        long e=execution(template(),"permissions"), t=task(e);
        db.update("UPDATE project SET owner_id=3 WHERE id=1");
        assertThrows(BusinessException.class,()->v4.executionDetail(e,owner()));
        assertEquals(0L,((Number)v4.myTasks(owner(),"ACTIVE",null,null,null,null,1,50).get("total")).longValue());
        db.update("UPDATE project SET owner_id=2 WHERE id=1");
        db.update("UPDATE knowledge_document SET status=0 WHERE id=1");
        assertFalse((Boolean)v4.executionDetail(e,owner()).get("sourceAvailable"));
        assertThrows(BusinessException.class,()->v4.createExecution(Map.of("templateVersionId",db.queryForObject("SELECT template_version_id FROM compliance_execution WHERE id=?",Long.class,e),"name","bad","idempotencyKey","off"),owner()));
        db.update("DELETE FROM knowledge_document WHERE id=1");
        assertThrows(BusinessException.class,()->v4.taskEvents(t,owner()));
        assertFalse(v4.listMyExecutions(owner(),null,null,null,null,1,50).toString().contains("六月独立"));
        assertFalse(legacy.history(2L).toString().contains("历史私人备注"));
    }
    @Test void rolloutPreservesLegacyAndStopsOldWritesAndReminders() {
        assertFalse(rollout.isV4(2L)); assertEquals(1,legacy.history(2L).size());
        assertEquals("历史私人备注",legacy.history(2L).get(0).get("remark"));
        rollout.enable(2L); rollout.enable(2L); assertEquals(1,n("compliance_user_mode"));
        db.update("UPDATE compliance_item SET title='其他人修改的旧事项' WHERE id=1");
        db.update("UPDATE compliance_user_progress SET remark='后来修改的旧备注' WHERE user_id=2");
        assertEquals("历史事项",legacy.history(2L).get(0).get("title"));
        assertEquals("历史私人备注",legacy.history(2L).get(0).get("remark"));
        assertThrows(BusinessException.class,()->old.updateMyProgress(2L,1L,"PROCESSING","bad",null));
        assertThrows(BusinessException.class,()->old.updateMyReminder(2L,1L,1,3));
        db.update("DELETE FROM compliance_user_progress"); v3scheduler.remindOn(LocalDate.now());
        assertEquals(0,n("notification")); assertEquals(0,n("compliance_reminder_log"));
        assertEquals(1,n("compliance_item"));
    }
    @Test void concurrentCreateSameKeyAndConcurrentReminderSendAreIdempotent() throws Exception {
        long version=template(); rollout.enable(2L);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->execution(version,"same")); var b=pool.submit(()->execution(version,"same"));
            long e=a.get(20,TimeUnit.SECONDS); assertEquals(e,b.get(20,TimeUnit.SECONDS)); assertEquals(1,n("compliance_execution"));
            long id=dueNode(e);
            var x=pool.submit(()->delivery.send(id)); var y=pool.submit(()->delivery.send(id)); x.get(20,TimeUnit.SECONDS); y.get(20,TimeUnit.SECONDS);
            assertEquals(1,n("notification")); assertEquals("SENT",db.queryForObject("SELECT state FROM compliance_execution_reminder WHERE id=?",String.class,id));
        } finally { pool.shutdownNow(); }
    }
    @Test void notificationFailureRollsBackAndBoundedRetryRecovers() {
        long id=dueNode(execution(template(),"retry")); AtomicInteger calls=new AtomicInteger();
        doAnswer(a->{ a.callRealMethod(); if(calls.getAndIncrement()==0) throw new IllegalStateException("fixture after insert"); return null; }).when(notifications).notify(anyLong(),anyString(),anyString(),anyString(),anyString());
        delivery.send(id); assertEquals(0,n("notification")); assertEquals("PENDING",db.queryForObject("SELECT state FROM compliance_execution_reminder WHERE id=?",String.class,id));
        delivery.send(id); assertEquals(1,n("notification")); delivery.send(id); assertEquals(1,n("notification"));
    }
    @Test void waitingRevisionRemindersAndPauseResumeArchive() {
        long e=execution(template(),"waiting"), t=task(e); v4.startExecution(e,Map.of(),owner());
        v4.patchTask(t,Map.of("dueDate",LocalDate.now().plusDays(7).toString(),"waiting",Map.of("reason","等待材料","nextAction","联系客户","reviewDate",LocalDate.now().plusDays(1).toString()),"rowVersion",0),owner());
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM compliance_execution_reminder WHERE state='PENDING'",Integer.class));
        v4.patchTask(t,Map.of("remindEnabled",0,"rowVersion",1),owner());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM compliance_execution_reminder WHERE state='PENDING'",Integer.class));
        v4.patchTask(t,Map.of("remindEnabled",1,"rowVersion",2),owner());
        v4.pauseOrArchive(e,"pause",owner()); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM compliance_execution_reminder WHERE state='PENDING'",Integer.class));
        v4.startExecution(e,Map.of(),owner());
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM compliance_execution_reminder WHERE state='PENDING'",Integer.class));
        v4.pauseOrArchive(e,"archive",owner()); assertThrows(BusinessException.class,()->v4.patchTask(t,Map.of("note","bad"),owner()));
    }
    @Test void realNullClearsAndStaleVersionRollsBackAllEvents() {
        long e=execution(template(),"null"),t=task(e);
        v4.patchTask(t,Map.of("answer",Map.of("optionCode","B"),"note","old","dueDate",LocalDate.now().plusDays(8).toString(),"rowVersion",0),owner());
        int before=n("compliance_execution_event");
        assertThrows(BusinessException.class,()->v4.patchTask(t,Map.of("note","stale","rowVersion",0),owner()));
        assertEquals(before,n("compliance_execution_event"));
        Map<String,Object> clear=new HashMap<>(); clear.put("answer",null); clear.put("note",null); clear.put("dueDate",null); clear.put("rowVersion",1);
        v4.patchTask(t,clear,owner());
        Map<String,Object> row=db.queryForMap("SELECT note,answer,due_date FROM compliance_execution_task WHERE id=?",t); assertTrue(row.values().stream().allMatch(Objects::isNull));
    }
    @Test void persistentFailureStopsAtThreeAndRollsBackInsertedNotifications() {
        long id=dueNode(execution(template(),"bounded"));
        doAnswer(a->{a.callRealMethod();throw new IllegalStateException("fixture persistent after insert");}).when(notifications).notify(anyLong(),anyString(),anyString(),anyString(),anyString());
        for (int i=0;i<5;i++) delivery.send(id);
        assertEquals(0,n("notification"));
        assertEquals("FAILED",db.queryForObject("SELECT state FROM compliance_execution_reminder WHERE id=?",String.class,id));
        assertEquals(3,db.queryForObject("SELECT attempt_count FROM compliance_execution_reminder WHERE id=?",Integer.class,id));
    }
    @Test void completedTaskWinsConcurrentDeliveryAndLeavesNoNotification() throws Exception {
        long e=execution(template(),"race"), t=task(e), id=dueNode(e);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            final Future<?>[] sender = new Future<?>[1];
            new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status->{
                rollout.lockUser(2L);
                sender[0]=pool.submit(()->delivery.send(id));
                v4.patchTask(t,Map.of("answer",Map.of("optionCode","A"),"progress","COMPLETED","rowVersion",0),owner());
            });
            sender[0].get(20,TimeUnit.SECONDS); assertEquals(0,n("notification"));
        } finally { pool.shutdownNow(); }
    }
    @Test void revokedProjectAndDisabledAccountCancelScheduledDelivery() {
        long id=dueNode(execution(template(),"revoked"));
        db.update("UPDATE project SET owner_id=3 WHERE id=1"); delivery.send(id);
        assertEquals("CANCELLED",db.queryForObject("SELECT state FROM compliance_execution_reminder WHERE id=?",String.class,id));
        assertEquals(0,n("notification"));
        db.update("UPDATE project SET owner_id=2 WHERE id=1");
        long e2=execution(db.queryForObject("SELECT id FROM compliance_template_version",Long.class),"inactive"), id2=dueNode(e2);
        db.update("UPDATE sys_user SET status=0 WHERE id=2"); delivery.send(id2);
        assertEquals("CANCELLED",db.queryForObject("SELECT state FROM compliance_execution_reminder WHERE id=?",String.class,id2));
        assertThrows(BusinessException.class,()->v4.executionDetail(e2,owner()));
        assertEquals(0,n("notification"));
    }
    @Test void concurrentDraftRevisionAllocationUsesCurrentLockedRead() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->v4.createDraft(1L,null,"draft-a",owner()));
            var b=pool.submit(()->v4.createDraft(1L,null,"draft-b",owner()));
            assertNotEquals(a.get(20,TimeUnit.SECONDS).get("revision"),b.get(20,TimeUnit.SECONDS).get("revision"));
            assertEquals(2,n("compliance_template_version"));
        } finally { pool.shutdownNow(); }
    }
    @Test void disablingFeatureDoesNotReactivateLegacyWritesOrReminders() {
        long id=dueNode(execution(template(),"kill-switch"));
        org.springframework.test.util.ReflectionTestUtils.setField(rollout,"enabled",false);
        try {
            assertTrue(rollout.isV4(2L)); assertFalse(rollout.isEnabled());
            assertThrows(BusinessException.class,()->old.updateMyProgress(2L,1L,"PROCESSING","bad",null));
            assertThrows(BusinessException.class,()->rollout.requireV4(2L));
            db.update("DELETE FROM compliance_user_progress"); v3scheduler.remindOn(LocalDate.now()); assertEquals(0,n("notification"));
            delivery.send(id); assertEquals(0,n("notification"));
            assertEquals("PENDING",db.queryForObject("SELECT state FROM compliance_execution_reminder WHERE id=?",String.class,id));
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(rollout,"enabled",true); }
    }
    @Test void copiedTemplateReferencesDocumentVersionRatherThanTemplateId() {
        db.update("INSERT INTO knowledge_doc_version(id,doc_id,version,upload_by) VALUES(999,1,1,'验收员工甲')");
        long source=template();
        long copy=((Number)v4.createDraft(1L,source,"new revision",owner()).get("id")).longValue();
        assertEquals(999L,db.queryForObject("SELECT source_document_version_id FROM compliance_template_version WHERE id=?",Long.class,copy));
    }
    /** Leave only synthetic baseline for the separate real-browser acceptance runner. */
    @AfterAll void browserBaseline() { seed(); }
}
