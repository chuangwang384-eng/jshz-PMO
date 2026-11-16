package com.jshz.expert;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.scheduler.ComplianceDueScheduler;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in only: dedicated 127.0.0.1:13306/acceptance, never a production schema. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB", matches="acceptance")
@SpringBootTest(properties={"logging.level.org.springframework.boot.autoconfigure.security=OFF", "expert.compliance.due-enabled=false", "expert.wecom.sync-cron-enabled=false", "expert.wecom.corp-id=", "expert.wecom.contact-secret="})
class IsolatedAcceptanceIT {
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"),"jshz-acceptance-0e8f3a4","files");
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13306/acceptance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        r.add("spring.datasource.username",()->"root");
        r.add("spring.datasource.password",()->System.getenv("ACCEPTANCE_PASSWORD"));
        r.add("expert.jwt.secret",()->UUID.randomUUID().toString()+UUID.randomUUID());
        for (String path : List.of("photo.storage-dir","photo.temp-dir","knowledge.storage-dir","knowledge.preview-dir","customer.storage-dir","supplier.storage-dir","project.storage-dir"))
            r.add("expert."+path,()->ROOT.resolve(path).toString());
    }
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired ProjectService projects;
    @Autowired SupplierService suppliers;
    @Autowired ProjectStorageService storage;
    @Autowired ComplianceService compliance;
    @Autowired ComplianceDueScheduler scheduler;
    @Autowired PlatformTransactionManager tx;
    @SpyBean NotificationService notifications;
    final LocalDate due=LocalDate.of(2026,11,10);
    AuthUser user(long id,String... perms) { return new AuthUser(id,"acceptance",false,false,Set.of(),Set.of(perms)); }
    @BeforeEach void fixtures() throws Exception {
        try(var c=dataSource.getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:13306/acceptance?"));
            assertEquals("acceptance",db.queryForObject("select database()",String.class));
        }
        reset(notifications);
        for(String table:List.of("notification","compliance_reminder_log","compliance_user_progress","compliance_item_assignee","compliance_item_history","knowledge_favorite","compliance_item","knowledge_document","operation_log","project_supplier","project_doc","project_executor","project","supplier","project_phase","project_template")) db.update("DELETE FROM "+table);
        db.update("INSERT INTO project(id,name,category,creator_id,owner_id,phase_id) VALUES(1,'acceptance','ONLINE',2,2,1)");
        db.update("INSERT INTO supplier(id,name,type_id,city_id) VALUES(1,'fixture supplier',1,1)");
        db.update("INSERT INTO project_phase(id,name,sort,is_active) VALUES(1,'fixture phase',1,1)");
        db.update("INSERT INTO project_template(id,name,template_type) VALUES(1,'fixture text','CUSTOM_TEXT')");
        db.update("INSERT INTO knowledge_document(id,category,title,status) VALUES(1,'enterprise-compliance','fixture',1)");
        db.update("INSERT INTO compliance_item(id,item_code,document_id,title,due_date,is_draft) VALUES(1,'fixture',1,'fixture',?,0)",due);
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES(1,5)");
    }
    int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    @Test void projectAuthorizationAndSupplierConsistency() {
        assertEquals(403,assertThrows(BusinessException.class,()->projects.timeline(1L,user(3))).getCode());
        projects.timeline(1L,user(2));
        db.update("INSERT INTO project_executor(project_id,user_id) VALUES(1,3)");
        projects.timeline(1L,user(3)); projects.timeline(1L,user(4,"project:view-all"));
        projects.addSupplier(1L,1L,user(2,"project:manage"));
        db.update("UPDATE project_supplier SET fee=100 WHERE project_id=1");
        assertThrows(BusinessException.class,()->suppliers.deleteSupplier(1L));
        assertEquals(1,count("supplier")); assertEquals(1,projects.listSuppliers(1L,user(2)).size());
        projects.removeSupplier(1L,1L,user(2,"project:manage")); suppliers.deleteSupplier(1L);
        assertEquals(0,count("supplier")); assertEquals(0,count("project_supplier"));
    }
    @Test void textFilesAndDatabaseRollbackStayConsistent() {
        projects.saveRichText(1L,1L,1L,"first",user(2)); projects.saveRichText(1L,1L,1L,"second",user(2));
        var paths=db.queryForList("SELECT file_path FROM project_doc ORDER BY id",String.class);
        assertEquals(2,new HashSet<>(paths).size()); assertEquals("first",storage.readRichText(paths.get(0)));
        Long first=db.queryForObject("SELECT MIN(id) FROM project_doc",Long.class);
        new TransactionTemplate(tx).executeWithoutResult(status->{projects.deleteDoc(1L,first,user(2));status.setRollbackOnly();});
        assertEquals("first",storage.readRichText(paths.get(0)));
        projects.deleteDoc(1L,first,user(2)); assertNull(storage.readRichText(paths.get(0)));
        assertEquals("second",storage.readRichText(paths.get(1)));
        new TransactionTemplate(tx).executeWithoutResult(status->{projects.saveRichText(1L,1L,1L,"rollback",user(2));status.setRollbackOnly();});
        assertEquals(2,count("project_doc"));
    }
    @Test void reminderDatesCancellationAndRealUniqueKey() {
        for(int day=4;day>=-1;day--) scheduler.remindOn(due.minusDays(day));
        scheduler.remindOn(due); assertEquals(2,count("notification"));
        db.update("DELETE FROM notification"); db.update("DELETE FROM compliance_reminder_log");
        db.update("INSERT INTO compliance_user_progress(item_id,user_id,remind_enabled) VALUES(1,5,1)");
        db.update("DELETE FROM knowledge_favorite"); scheduler.remindOn(due); assertEquals(0,count("notification"));
    }
    @Test void retryAndPerNodeTransactionsDoNotDuplicateOrRollbackEarlierRecipients() {
        AtomicInteger failures=new AtomicInteger();
        doAnswer(a->{if(failures.getAndIncrement()==0) throw new IllegalStateException("fixture failure");return a.callRealMethod();})
            .when(notifications).notify(anyLong(),anyString(),anyString(),anyString(),anyString());
        scheduler.remindOn(due); assertEquals(1,count("notification"));
        assertEquals("SENT",db.queryForObject("SELECT send_status FROM compliance_reminder_log",String.class));
        assertEquals(1,db.queryForObject("SELECT retry_count FROM compliance_reminder_log",Integer.class));
        db.update("INSERT INTO knowledge_favorite(doc_id,user_id) VALUES(1,6)");
        doAnswer(a->{if(a.getArgument(0).equals(6L))throw new IllegalStateException("fixture persistent");return a.callRealMethod();})
            .when(notifications).notify(anyLong(),anyString(),anyString(),anyString(),anyString());
        scheduler.remindOn(due); assertEquals(1,count("notification"));
        assertEquals("FAILED",db.queryForObject("SELECT send_status FROM compliance_reminder_log WHERE user_id=6",String.class));
    }
    @Test void concurrentSchedulersProduceOneNotification() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try { var a=pool.submit(()->scheduler.remindOn(due)); var b=pool.submit(()->scheduler.remindOn(due));a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS); }
        finally {pool.shutdownNow();}
        assertEquals(1,count("notification")); assertEquals(1,count("compliance_reminder_log"));
    }
    @Test void supplierDeletionWaitsForCommittedProjectAssociation() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();
        CountDownLatch started=new CountDownLatch(1);
        try {
            new TransactionTemplate(tx).executeWithoutResult(status->{
                projects.addSupplier(1L,1L,user(2,"project:manage"));
                Future<?> deletion=pool.submit(()->{started.countDown(); assertThrows(BusinessException.class,()->suppliers.deleteSupplier(1L));});
                try { assertTrue(started.await(3,TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class,()->deletion.get(300,TimeUnit.MILLISECONDS));
                } catch(InterruptedException e) {throw new RuntimeException(e);}
            });
            pool.shutdown(); assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));
            assertEquals(1,count("supplier")); assertEquals(1,count("project_supplier"));
        } finally {pool.shutdownNow();}
    }
    @Test void completedDisabledUnpublishedAndResponsibilityEligibility() {
        db.update("INSERT INTO compliance_user_progress(item_id,user_id,remind_enabled) VALUES(1,5,0)");
        scheduler.remindOn(due); assertEquals(0,count("notification"));
        db.update("UPDATE compliance_user_progress SET remind_enabled=1,progress_status='COMPLETED'");
        scheduler.remindOn(due); assertEquals(0,count("notification"));
        db.update("DELETE FROM compliance_user_progress"); db.update("UPDATE knowledge_document SET status=0");
        scheduler.remindOn(due); assertEquals(0,count("notification"));
        db.update("UPDATE knowledge_document SET status=1"); db.update("DELETE FROM knowledge_favorite");
        db.update("INSERT INTO compliance_item_assignee(item_id,user_id) VALUES(1,5)");
        scheduler.remindOn(due); assertEquals(1,count("notification"));
    }
    @Test void legacySharedTextAndAssignmentNotification() throws Exception {
        projects.saveRichText(1L,1L,1L,"shared",user(2));
        Long original=db.queryForObject("SELECT id FROM project_doc",Long.class);
        String path=db.queryForObject("SELECT file_path FROM project_doc",String.class);
        db.update("INSERT INTO project_doc(project_id,phase_id,file_name,file_path,upload_by,is_deleted) VALUES(1,1,'fixture',?,'acceptance',0)",path);
        projects.deleteDoc(1L,original,user(2)); assertEquals("shared",storage.readRichText(path));
        Long other=db.queryForObject("SELECT id FROM project_doc WHERE is_deleted=0",Long.class);
        projects.deleteDoc(1L,other,user(2)); assertNull(storage.readRichText(path));
        db.update("INSERT INTO compliance_item_assignee(item_id,user_id) VALUES(1,5)");
        compliance.updateItem(1L,Map.of("assigneeIds",List.of(5L,6L)),user(2));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=5",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=6",Integer.class));
    }
}
