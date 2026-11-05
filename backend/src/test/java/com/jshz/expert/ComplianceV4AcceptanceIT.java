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
    @Autowired ComplianceTemplateWorkspaceService workspace;
    @Autowired ComplianceWorkspaceSourceService workspaceSources;
    @Autowired KnowledgeDocStorageService workspaceStorage;
    @Autowired ComplianceV4Service v4;
    @Autowired ComplianceService old;
    @Autowired ComplianceRolloutService rollout;
    @Autowired ComplianceLegacyService legacy;
    @Autowired ComplianceV4ReminderService delivery;
    @Autowired ComplianceDueScheduler v3scheduler;
    @Autowired ProjectConfigurationService projectConfiguration;
    @Autowired ProjectService projects;
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
        for (String t : List.of("compliance_template_review", "compliance_template_extraction", "compliance_template_workspace_request", "compliance_template_workspace", "notification", "compliance_execution_reminder", "compliance_execution_event", "compliance_execution_task", "compliance_execution", "compliance_template_option", "compliance_template_node", "compliance_template_version", "compliance_user_mode", "compliance_reminder_log", "compliance_user_progress", "compliance_item_assignee", "compliance_item", "knowledge_favorite", "knowledge_doc_version", "knowledge_document", "project_executor", "project_config_snapshot", "project", "project_phase", "user_role", "role_permission", "role", "permission", "sys_user")) db.update("DELETE FROM "+t);
        String pass = encoder.encode(System.getenv("V4_TEST_PASSWORD"));
        for (int i=1;i<=3;i++) db.update("INSERT INTO sys_user(id,username,password,is_admin,must_change_password,status) VALUES(?,?,?,0,0,1)", i, i==1?"验收维护人":i==2?"验收员工甲":"验收员工乙",pass);
        db.update("INSERT INTO role(id,code,name) VALUES(1,'FIXTURE','fixture')");
        db.update("INSERT INTO permission(code,name,type) VALUES('knowledge:maintain','fixture',1)");
        db.update("INSERT INTO role_permission(role_id,permission_code) VALUES(1,'knowledge:maintain')");
        db.update("INSERT INTO user_role(user_id,role_id) VALUES(1,1)");
        db.update("INSERT INTO knowledge_document(id,category,title,upload_by,status,content) VALUES(1,'enterprise-compliance','隔离规范','验收员工甲',1,'隔离示例条款')");
        db.update("INSERT INTO project_phase(id,name,sort,is_active) VALUES(1,'验收阶段',1,1)");
        db.update("INSERT INTO project(id,name,category,creator_id,owner_id,phase_id,status) VALUES(1,'隔离项目','ONLINE',2,2,1,'ACTIVE')");
        Map<String,Object> phase = Map.of("phaseId",1,"name","验收阶段","position",1,"mode","NONE","requirements",List.of());
        Map<String,Object> snapshot = Map.of("schemaVersion",1,"name","验收类型","phases",List.of(phase));
        db.update("INSERT INTO project_config_snapshot(project_id,schema_version,effective_config,config_hash,origin) VALUES(1,1,?,?,'CREATED')", projectConfiguration.encode(snapshot), projectConfiguration.configHash(snapshot));
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
    @Test void workspaceSelectionActivationAndExecutionIsolation() {
        db.update("DELETE FROM compliance_template_workspace_request");
        db.update("DELETE FROM compliance_template_workspace");
        long original=template();
        long execution=execution(original,"workspace-original");
        String before=db.queryForObject("SELECT CAST(snapshot AS CHAR) FROM compliance_execution_task WHERE execution_id=?",String.class,execution);
        long second=template();
        var initial=workspace.open(1L,reviewer());
        assertEquals(false,initial.get("selectionResolved"));
        var selected=workspace.select(1L,Map.of("selectedId",original,"expectedRowVersion",initial.get("rowVersion"),"requestId",UUID.randomUUID().toString()),reviewer());
        assertEquals(original,((Number)selected.get("activeTemplateVersionId")).longValue());
        Map<String,Object> content=new LinkedHashMap<>(Map.of("title","更新后的模板","nodes",List.of(Map.of("nodeCode","new-item","title","新增事项","inputType","CHECK"))));
        var saved=workspace.save(1L,Map.of("content",content,"expectedRowVersion",selected.get("rowVersion")),reviewer());
        assertThrows(BusinessException.class,()->workspace.save(1L,Map.of("content",content,"expectedRowVersion",selected.get("rowVersion")),reviewer()));
        var activation=Map.<String,Object>of("expectedRowVersion",saved.get("rowVersion"),"requestId",UUID.randomUUID().toString());
        var active=workspace.activate(1L,activation,reviewer());
        assertEquals(((Number)active.get("activeTemplateVersionId")).longValue(),((Number)workspace.activate(1L,activation,reviewer()).get("activeTemplateVersionId")).longValue());
        assertEquals(1,v4.listPublishedTemplates(1L,owner()).size());
        assertEquals(before,db.queryForObject("SELECT CAST(snapshot AS CHAR) FROM compliance_execution_task WHERE execution_id=?",String.class,execution));
        assertThrows(BusinessException.class,()->workspace.activate(1L,Map.of("expectedRowVersion",active.get("rowVersion"),"requestId",UUID.randomUUID().toString()),owner()));
    }    @Test @SuppressWarnings("unchecked") void workspaceSourceMergeReviewAndStaleVersion() throws Exception {
        Path file=workspaceStorage.resolveDocument("workspace-source.pdf"); Files.createDirectories(file.getParent());
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)) {
                stream.beginText();stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA,12);stream.newLineAtOffset(40,700);stream.showText("Submit compliance materials before the meeting date.");stream.endText();
            } pdf.save(file.toFile());
        }
        db.update("UPDATE knowledge_document SET file_path='workspace-source.pdf',file_name='workspace-source.pdf',version=1 WHERE id=1");
        var extraction=workspaceSources.extract(1L,reviewer());
        var candidates=(List<Map<String,Object>>)extraction.get("candidates");assertFalse(candidates.isEmpty());
        var opened=workspace.open(1L,reviewer());
        var saved=workspace.save(1L,Map.of("expectedRowVersion",opened.get("rowVersion"),"content",Map.of("title","来源校验模板","nodes",List.of())),reviewer());
        var mergeBody=Map.<String,Object>of("expectedRowVersion",saved.get("rowVersion"),"requestId",UUID.randomUUID().toString(),"extractionId",extraction.get("extractionId"),"actions",List.of(Map.of("candidateId",candidates.get(0).get("candidateId"))));
        var merged=workspace.merge(1L,mergeBody,reviewer());
        var content=(Map<String,Object>)merged.get("content");var nodes=(List<Map<String,Object>>)content.get("nodes");assertEquals(1,nodes.size());
        assertThrows(BusinessException.class,()->workspace.activate(1L,Map.of("expectedRowVersion",merged.get("rowVersion"),"requestId",UUID.randomUUID().toString()),reviewer()));
        var reviewed=workspace.review(1L,Map.of("expectedRowVersion",merged.get("rowVersion"),"nodeCode",nodes.get(0).get("nodeCode")),reviewer());
        var active=workspace.activate(1L,Map.of("expectedRowVersion",reviewed.get("rowVersion"),"requestId",UUID.randomUUID().toString()),reviewer());assertNotNull(active.get("activeTemplateVersionId"));
        var edited=(Map<String,Object>)active.get("content");var editedNodes=(List<Map<String,Object>>)edited.get("nodes");editedNodes.get(0).put("title","修改后的要求");
        var changed=workspace.save(1L,Map.of("content",edited,"expectedRowVersion",active.get("rowVersion")),reviewer());
        assertThrows(BusinessException.class,()->workspace.activate(1L,Map.of("expectedRowVersion",changed.get("rowVersion"),"requestId",UUID.randomUUID().toString()),reviewer()));
        var source=workspaceSources.decode(editedNodes.get(0).get("sourceAnchor").toString());source.put("excerpt","forged excerpt");editedNodes.get(0).put("sourceAnchor",workspaceSources.encode(source));
        assertThrows(BusinessException.class,()->workspace.save(1L,Map.of("content",edited,"expectedRowVersion",changed.get("rowVersion")),reviewer()));
        db.update("UPDATE knowledge_document SET version=2 WHERE id=1");
        assertThrows(BusinessException.class,()->workspaceSources.extraction(1L,extraction.get("extractionId").toString(),reviewer()));
        assertThrows(BusinessException.class,()->workspaceSources.extract(1L,other()));
        Files.deleteIfExists(file);
    }
    @Test void workspaceIncompleteExtractionRequiresConfirmation() throws Exception {
        Path file=workspaceStorage.resolveDocument("workspace-unsupported.txt");Files.createDirectories(file.getParent());Files.writeString(file,"Manual source verification fixture");
        db.update("UPDATE knowledge_document SET file_path='workspace-unsupported.txt',file_name='workspace-unsupported.txt',version=1 WHERE id=1");
        var extraction=workspaceSources.extract(1L,reviewer());assertEquals(true,extraction.get("incomplete"));
        var opened=workspace.open(1L,reviewer());
        var saved=workspace.save(1L,Map.of("content",Map.of("title","人工补齐模板","nodes",List.of(Map.of("nodeCode","manual-1","title","人工补充事项","inputType","CHECK"))),"expectedRowVersion",opened.get("rowVersion")),reviewer());
        var merged=workspace.merge(1L,Map.of("expectedRowVersion",saved.get("rowVersion"),"extractionId",extraction.get("extractionId"),"actions",List.of(),"requestId",UUID.randomUUID().toString()),reviewer());
        assertThrows(BusinessException.class,()->workspace.activate(1L,Map.of("expectedRowVersion",merged.get("rowVersion"),"requestId",UUID.randomUUID().toString()),reviewer()));
        workspace.confirmExtraction(1L,Map.of("extractionId",extraction.get("extractionId"),"confirmed",true),reviewer());
        assertNotNull(workspace.activate(1L,Map.of("expectedRowVersion",merged.get("rowVersion"),"requestId",UUID.randomUUID().toString()),reviewer()).get("activeTemplateVersionId"));
        Files.deleteIfExists(file);
    }
    @Test void browserFixture() {
        assertEquals(3, n("sys_user"));
        assertEquals(1, n("project"));
        assertEquals("隔离项目", db.queryForObject("SELECT name FROM project WHERE id=1",String.class));
        assertEquals(1, projects.pageProjects(null,null,null,null,null,null,null,null,null,1,100,owner()).getTotal());
    }
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
