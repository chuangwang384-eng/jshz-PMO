package com.jshz.expert;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PC_ACCEPTANCE_DB",matches="pc_acceptance")
@SpringBootTest(properties={"logging.level.org.springframework.boot.autoconfigure.security=ERROR","expert.compliance.v4-enabled=false","expert.compliance.v4-reminder-enabled=false","expert.compliance.due-enabled=false","expert.wecom.sync-cron-enabled=false","expert.wecom.corp-id=","expert.wecom.contact-secret="})
class ProjectConfigurationAcceptanceIT {
    @DynamicPropertySource static void isolation(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13317/pc_acceptance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        r.add("spring.datasource.username",()->"root");r.add("spring.datasource.password",()->System.getenv("PC_DB_PASSWORD"));
        r.add("expert.jwt.secret",()->UUID.randomUUID().toString()+UUID.randomUUID());
        for(String key:List.of("photo.storage-dir","photo.temp-dir","knowledge.storage-dir","knowledge.preview-dir","customer.storage-dir","supplier.storage-dir","project.storage-dir"))
            r.add("expert."+key,()->Path.of(System.getProperty("java.io.tmpdir"),"jshz-pc-acceptance",key).toString());
    }
    @Autowired JdbcTemplate db;
    @Autowired ProjectConfigurationService config;
    @Autowired ProjectConfigurationMigration migration;
    @Autowired ProjectService projects;
    @Autowired PlatformTransactionManager tx;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    final AuthUser manager=new AuthUser(1L,"配置验收维护人",true,false,Set.of(),Set.of("project:create","project:manage"));
    final AuthUser stranger=new AuthUser(2L,"配置验收旁观者",false,false,Set.of(),Set.of());
    @BeforeEach void seed() {
        assertEquals("pc_acceptance",db.queryForObject("SELECT DATABASE()",String.class));
        assertTrue(db.queryForObject("SELECT @@datadir",String.class).contains(".build-project-config-acceptance"));
        assertEquals(1,db.queryForObject("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_type' AND INDEX_NAME='uk_project_type_position' AND NON_UNIQUE=0",Integer.class));
        for(String t:List.of("project_config_request","project_config_snapshot","project_type_config_revision","project_phase_history","project_phase_template_rel","project_type_phase_rel","project_executor","project_expert","project_supplier","project_doc","project_contract","project","project_type","project_phase","project_template","operation_log")) db.update("DELETE FROM "+t);
        db.update("UPDATE project_config_state SET epoch=0 WHERE id=1");
        db.update("INSERT INTO project_phase(id,name,sort,is_active) VALUES (101,'立项',10,1),(102,'执行',80,1),(103,'结束',300,1)");
        db.update("INSERT INTO project_template(id,name,template_type) VALUES (101,'资料附件','FILE'),(102,'阶段说明','RICH_TEXT'),(103,'预算金额','BUDGET')");
        db.update("INSERT INTO project_phase_template_rel(type_id,phase_id,template_id,required,sort) VALUES (0,101,101,1,1)");
    }
    Map<String,Object> stage(long phase,String mode,List<Map<String,Object>> req) {return new LinkedHashMap<>(Map.of("phaseId",phase,"mode",mode,"requirements",req));}
    Map<String,Object> req(long id,int required){return Map.of("templateId",id,"required",required);}
    Map<String,Object> body(String name,int position,List<Map<String,Object>> phases) {
        return new LinkedHashMap<>(Map.of("name",name,"position",position,"phases",phases,"expectedEpoch",config.context(manager).get("expectedEpoch"),"requestId",UUID.randomUUID().toString()));
    }
    long save(String name,int pos,List<Map<String,Object>> stages){return ((Number)config.save(null,body(name,pos,stages),manager).get("id")).longValue();}
    Project create(long type){return projects.create(new LinkedHashMap<>(Map.of("name","隔离项目","category","OFFLINE","typeId",type,"ownerId",1)),manager);}
    @Test void newStageIsAtomicAndDoesNotAffectOtherType() {
        long first=save("原类型",1,List.of(stage(101,"NONE",List.of())));
        Map<String,Object> added=new LinkedHashMap<>(Map.of("name","现场验收","mode","NONE","requirements",List.of()));
        Map<String,Object> draft=body("新类型",2,List.of(added));
        config.preview(draft,manager);assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM project_phase WHERE name='现场验收'",Integer.class));
        config.save(null,draft,manager);assertEquals(1,ProjectConfigurationService.rows(config.definition(first).get("phases")).size());
        long phases=db.queryForObject("SELECT COUNT(*) FROM project_phase",Long.class);
        added.put("name","失败不落库");draft=body("原类型",2,List.of(added));
        Map<String,Object> invalid=draft;assertThrows(BusinessException.class,()->config.save(null,invalid,manager));
        assertEquals(phases,db.queryForObject("SELECT COUNT(*) FROM project_phase",Long.class));
    }
    @Test void optionalCustomAndNoneDoNotFallBackToCommon() {
        long custom=save("全选填",1,List.of(stage(101,"CUSTOM",List.of(req(101,0))),stage(102,"NONE",List.of())));
        Project p=create(custom);projects.changePhase(p.getId(),102L,null,manager);
        long none=save("无要求",2,List.of(stage(101,"NONE",List.of()),stage(102,"NONE",List.of())));
        Project second=create(none);projects.changePhase(second.getId(),102L,null,manager);
    }
    @Test void commonRequiredBlocksCurrentPhaseUntilMaterialExists() {
        long type=save("沿用",1,List.of(stage(101,"INHERIT",List.of()),stage(102,"NONE",List.of())));
        Project p=create(type);assertThrows(BusinessException.class,()->projects.changePhase(p.getId(),102L,null,manager));
        db.update("INSERT INTO project_doc(project_id,phase_id,file_name,file_path) VALUES (?,101,'虚构资料.pdf','fixture/file.pdf')",p.getId());
        projects.changePhase(p.getId(),102L,null,manager);
        long budgetType=save("零预算",2,List.of(stage(101,"CUSTOM",List.of(req(103,1))),stage(102,"NONE",List.of())));
        Project budgetProject=create(budgetType);
        assertThrows(BusinessException.class,()->projects.changePhase(budgetProject.getId(),102L,null,manager));
        projects.registerBudget(budgetProject.getId(),java.math.BigDecimal.ZERO,manager);
        Map<String,Object> first=configurationPhases(budgetProject.getId()).get(0);
        assertEquals(101L,((Number)ProjectConfigurationService.rows(first.get("templates")).get(0).get("phaseId")).longValue());
        projects.changePhase(budgetProject.getId(),102L,null,manager);
    }
    private List<Map<String,Object>> configurationPhases(long projectId) {
        return ProjectConfigurationService.rows(projects.detail(projectId,manager).get("phases"));
    }
    @Test void attachmentCannotSatisfyTextAndFrozenTextTypeSurvivesDictionaryChanges() {
        long type=save("材料分流",1,List.of(stage(101,"CUSTOM",List.of(req(101,1),req(102,1))),stage(102,"NONE",List.of())));
        Project p=create(type);
        db.update("INSERT INTO project_doc(project_id,phase_id,file_name,file_path) VALUES (?,101,'虚构附件.txt','fixture/attachment.txt')",p.getId());
        assertThrows(BusinessException.class,()->projects.changePhase(p.getId(),102L,null,manager));
        projects.updateTemplate(102L,"后续改为附件","FILE",manager);
        projects.saveRichText(p.getId(),101L,102L,"<p>隔离验收文本</p>",manager);
        assertEquals("RICH_TEXT",config.requirements(p.getId(),101L).get(1).get("templateType"));
        projects.changePhase(p.getId(),102L,null,manager);
    }
    @Test void oldProjectsFreezeOrderNamesRequirementsAndDisabledStages() {
        long type=save("快照",1,List.of(stage(101,"NONE",List.of()),stage(102,"NONE",List.of()),stage(103,"NONE",List.of())));
        Project p=create(type);String old=config.encode(config.snapshot(p.getId()));
        config.save(type,body("新类型名称",1,List.of(stage(103,"NONE",List.of()),stage(101,"INHERIT",List.of()))),manager);
        projects.updatePhase(102L,"执行已更名",-500,0,manager);
        projects.changePhase(p.getId(),102L,null,manager);
        assertThrows(BusinessException.class,()->projects.changePhase(p.getId(),101L,"",manager));
        projects.changePhase(p.getId(),101L,"隔离回退说明",manager);
        assertEquals(old,config.encode(config.snapshot(p.getId())));
        assertEquals(103L,create(type).getPhaseId());
        assertEquals("快照",projects.detail(p.getId(),manager).get("typeName"));
    }
    @Test void inheritCommonChangesOnlyFutureProjects() {
        long type=save("通用冻结",1,List.of(stage(101,"INHERIT",List.of())));Project old=create(type);
        projects.savePhaseTemplates(101L,List.of(Map.of("templateId",103,"required",1,"sort",1)),manager);
        assertEquals("FILE",config.requirements(old.getId(),101L).get(0).get("templateType"));
        assertEquals("BUDGET",config.requirements(create(type).getId(),101L).get(0).get("templateType"));
    }
    @Test void ranksRemainContinuousAcrossMoveAndDeleteWithUniqueConstraint() {
        long a=save("A",1,List.of(stage(101,"NONE",List.of())));
        long b=save("B",1,List.of(stage(101,"NONE",List.of())));
        assertEquals(List.of(b,a),db.queryForList("SELECT id FROM project_type ORDER BY sort",Long.class));
        config.order(Map.of("ids",List.of(a,b),"expectedEpoch",config.context(manager).get("expectedEpoch")),manager);
        projects.deleteType(a,manager);assertEquals(List.of(1),db.queryForList("SELECT sort FROM project_type ORDER BY sort",Integer.class));
        assertThrows(BusinessException.class,()->config.order(Map.of("ids",List.of(b,b),"expectedEpoch",config.context(manager).get("expectedEpoch")),manager));
    }
    @Test void concurrentSavesYieldOneWinnerAndOneConflict() throws Exception {
        Map<String,Object> a=body("并发甲",1,List.of(stage(101,"NONE",List.of()))),b=body("并发乙",1,List.of(stage(101,"NONE",List.of())));
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(Map<String,Object> input:List.of(a,b)) results.add(pool.submit(()->{start.await();try{config.save(null,input,manager);return true;}catch(BusinessException e){assertTrue(e.getMessage().contains("他人修改"));return false;}}));
            start.countDown();int winners=0;for(Future<Boolean> f:results)if(f.get(20,TimeUnit.SECONDS))winners++;
            assertEquals(1,winners);assertEquals(List.of(1),db.queryForList("SELECT sort FROM project_type",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void retryIsIdempotentButChangedPayloadWithSameKeyIsRejected() throws Exception {
        Map<String,Object> input=body("重试",1,List.of(stage(101,"NONE",List.of())));
        Map<String,Object> first=config.save(null,input,manager);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(mapper.readTree(config.encode(first)),mapper.readTree(config.encode(config.save(null,new TreeMap<>(input),manager))));
        input.put("name","不同请求");assertThrows(BusinessException.class,()->config.save(null,input,manager));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM project_type",Integer.class));
    }
    @Test void validationAndPermissionsRejectWithoutPartialWrites() {
        assertThrows(BusinessException.class,()->config.context(stranger));
        assertThrows(BusinessException.class,()->config.save(null,body("禁止",1,List.of(stage(101,"NONE",List.of()))),stranger));
        assertThrows(BusinessException.class,()->config.preview(body("空流程",1,List.of()),manager));
        assertThrows(BusinessException.class,()->config.preview(body("重复",1,List.of(stage(101,"NONE",List.of()),stage(101,"NONE",List.of()))),manager));
        assertThrows(BusinessException.class,()->config.preview(body("空专属",1,List.of(stage(101,"CUSTOM",List.of()))),manager));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM project_type",Integer.class));
    }
    @Test void typeSwitchAndDirectPhaseEditAreBlocked() {
        long type=save("不可换型",1,List.of(stage(101,"NONE",List.of()),stage(102,"NONE",List.of())));Project p=create(type);
        assertThrows(BusinessException.class,()->projects.update(p.getId(),Map.of("typeId",type+1),manager));
        assertThrows(BusinessException.class,()->projects.update(p.getId(),Map.of("phaseId",102),manager));
        assertThrows(BusinessException.class,()->projects.detail(p.getId(),stranger));
        assertThrows(BusinessException.class,()->projects.projectConfiguration(p.getId(),stranger));
    }
    @Test void missingCorruptAndUnknownVersionSnapshotsNeverFallBackToLiveConfig() {
        long type=save("校验快照",1,List.of(stage(101,"NONE",List.of())));Project p=create(type);
        db.update("UPDATE project_config_snapshot SET schema_version=99 WHERE project_id=?",p.getId());
        assertThrows(BusinessException.class,()->config.snapshot(p.getId()));
        db.update("UPDATE project_config_snapshot SET schema_version=1,config_hash=REPEAT('0',64) WHERE project_id=?",p.getId());
        assertThrows(BusinessException.class,()->config.snapshot(p.getId()));
        db.update("DELETE FROM project_config_snapshot WHERE project_id=?",p.getId());
        assertThrows(BusinessException.class,()->config.snapshot(p.getId()));
    }
    @Test void historicalReferencesCannotBeDeleted() {
        long type=save("历史",1,List.of(stage(101,"CUSTOM",List.of(req(101,0)))));create(type);
        assertThrows(BusinessException.class,()->projects.deleteTemplate(101L,manager));
        assertThrows(BusinessException.class,()->projects.deletePhase(101L,manager));
    }
    @Test void legacyWritersPublishCompleteDefinitionsAndPreserveExistingProjects() {
        long type=projects.createType("旧客户端类型",null,manager).getId();
        assertEquals(3,ProjectConfigurationService.rows(config.definition(type).get("phases")).size());
        projects.createPhase("后加公共阶段",999,manager);
        assertEquals(3,ProjectConfigurationService.rows(config.definition(type).get("phases")).size());
        projects.saveTypePhases(type,List.of(102L,101L),manager);
        projects.saveTypePhaseTemplates(type,102L,List.of(Map.of("templateId",101,"required",0,"sort",1)),manager);
        Map<String,Object> legacyRead=projects.listTypePhaseTemplates(type,102L).get(0);
        assertNotNull(legacyRead.get("id"));
        assertEquals(type,((Number)legacyRead.get("typeId")).longValue());
        assertEquals(102L,((Number)legacyRead.get("phaseId")).longValue());
        Project old=create(type);Map<String,Object> snapshot=config.snapshot(old.getId());
        projects.updateType(type,"旧客户端改名",1,manager);
        assertEquals("旧客户端改名",config.definition(type).get("name"));
        assertEquals(config.configHash(snapshot),config.configHash(config.snapshot(old.getId())));
        projects.changePhase(old.getId(),101L,null,manager); // all-optional legacy write is CUSTOM, not fallback.
        assertThrows(BusinessException.class,()->projects.saveTypePhaseTemplates(type,102L,List.of(),manager));
        assertThrows(BusinessException.class,()->projects.saveTypePhases(type,List.of(),manager));
    }
    @Test void legacyWritersStillEnforcePermissionAndUniquePositions() {
        assertThrows(BusinessException.class,()->projects.createType("越权旧客户端",null,stranger));
        long a=projects.createType("旧类型甲",0,manager).getId();
        long b=projects.createType("旧类型乙",1,manager).getId();
        assertEquals(List.of(b,a),db.queryForList("SELECT id FROM project_type ORDER BY sort",Long.class));
        projects.updateType(a,null,0,manager);
        assertEquals(List.of(a,b),db.queryForList("SELECT id FROM project_type ORDER BY sort",Long.class));
        assertThrows(BusinessException.class,()->projects.updateType(a,"禁止改名",1,stranger));
        assertThrows(BusinessException.class,()->projects.saveTypePhases(a,List.of(101L),stranger));
        assertThrows(BusinessException.class,()->projects.createPhase("越权阶段",1,stranger));
        assertThrows(BusinessException.class,()->projects.updatePhase(101L,"越权改名",1,1,stranger));
        assertThrows(BusinessException.class,()->projects.deletePhase(101L,stranger));
        assertThrows(BusinessException.class,()->projects.deleteType(a,stranger));
        assertThrows(BusinessException.class,()->projects.createTemplate("越权模板","FILE",stranger));
        assertThrows(BusinessException.class,()->projects.updateTemplate(101L,"越权模板","FILE",stranger));
        assertThrows(BusinessException.class,()->projects.deleteTemplate(101L,stranger));
        assertEquals(List.of(1,2),db.queryForList("SELECT sort FROM project_type ORDER BY sort",Integer.class));
    }
    @Test void migrationFreezesImplicitFlowAndIsRepeatable() {
        db.update("INSERT INTO project_type(id,name,sort) VALUES(901,'隐式类型',1),(902,'第二类型',2)");
        db.update("INSERT INTO project(id,name,category,type_id,owner_id,creator_id,phase_id) VALUES(901,'存量项目','OFFLINE',901,1,1,101)");
        assertThrows(BusinessException.class,()->migration.migrate(false));
        migration.migrate(true);String before=config.encode(config.snapshot(901));migration.migrate(false);
        assertEquals(before,config.encode(config.snapshot(901)));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM project_type_config_revision",Integer.class));
        projects.createPhase("新公共阶段",999,manager);
        assertEquals(3,ProjectConfigurationService.rows(config.definition(901).get("phases")).size());
    }
    @Test void legacyDuplicatePositionsAreNormalizedBeforeAddingUniqueIndex() {
        db.execute("ALTER TABLE project_type DROP INDEX uk_project_type_position");
        try {
            db.update("INSERT INTO project_type(id,name,sort) VALUES(901,'旧同位甲',0),(902,'旧同位乙',0)");
            migration.migrate(true);
            assertEquals(List.of(901L,902L),db.queryForList("SELECT id FROM project_type ORDER BY sort",Long.class));
            assertEquals(List.of(1,2),db.queryForList("SELECT sort FROM project_type ORDER BY sort",Integer.class));
        } finally {
            new TransactionTemplate(tx).executeWithoutResult(status->{config.lock();config.compact();});
            db.execute("ALTER TABLE project_type ADD UNIQUE KEY uk_project_type_position(sort)");
        }
    }
    @Test void migrationBlocksAnomaliesAndRequiresOptionalDifferenceReview() {
        db.update("INSERT INTO project_type(id,name,sort) VALUES(901,'异常类型',1)");
        db.update("INSERT INTO project_type_phase_rel(type_id,phase_id,sort) VALUES(901,101,1)");
        db.update("INSERT INTO project(id,name,category,type_id,owner_id,creator_id,phase_id) VALUES(901,'异常项目','OFFLINE',901,1,1,103)");
        assertThrows(BusinessException.class,()->migration.migrate(true));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM project_config_snapshot",Integer.class));
        db.update("UPDATE project SET phase_id=101 WHERE id=901");
        db.update("INSERT INTO project_phase_template_rel(type_id,phase_id,template_id,required,sort) VALUES(901,101,102,0,1)");
        assertThrows(BusinessException.class,()->migration.migrate(false));migration.migrate(true);
        assertEquals("FILE",config.requirements(901,101).get(0).get("templateType"));
        assertEquals("RICH_TEXT",ProjectConfigurationService.rows(ProjectConfigurationService.rows(config.definition(901).get("phases")).get(0).get("requirements")).get(0).get("templateType"));
        migration.migrate(false); // Reviewed differences and frozen projects do not re-block a retry.
    }
    @Test void deletedOrphanExclusionRequiresExplicitPolicyAndNeverExcludesLiveProjects() {
        db.update("INSERT INTO project(id,name,category,type_id,owner_id,creator_id,phase_id,status) VALUES(901,'已删孤儿','OFFLINE',999,1,1,101,'DELETED')");
        assertFalse(((List<?>)migration.preflight().get("anomalies")).isEmpty());
        assertThrows(BusinessException.class,()->migration.migrate(false));
        Map<String,Object> report=migration.migrate(false,true);
        assertEquals(1,((List<?>)report.get("excludedDeletedOrphans")).size());
        assertEquals("DELETED",db.queryForObject("SELECT status FROM project WHERE id=901",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM project_config_snapshot WHERE project_id=901",Integer.class));
        db.update("UPDATE project SET status='ACTIVE' WHERE id=901");
        assertThrows(BusinessException.class,()->migration.migrate(false,true));
        db.update("UPDATE project SET status='DELETED',type_id=NULL WHERE id=901");
        assertThrows(BusinessException.class,()->migration.migrate(false,true));
    }
    @Test void concurrentCreateAndPublishProduceCoherentSnapshots() throws Exception {
        long type=save("原配置",1,List.of(stage(101,"NONE",List.of())));
        Map<String,Object> input=body("新配置",1,List.of(stage(102,"NONE",List.of())));
        ExecutorService pool=Executors.newFixedThreadPool(2);try {
            Future<Project> creation=pool.submit(()->create(type));Future<?> publish=pool.submit(()->config.save(type,input,manager));
            Project p=creation.get(20,TimeUnit.SECONDS);publish.get(20,TimeUnit.SECONDS);
            Map<String,Object> snap=config.snapshot(p.getId());long phase=ProjectConfigurationService.rows(snap.get("phases")).stream().map(r->((Number)r.get("phaseId")).longValue()).findFirst().orElseThrow();
            assertEquals(p.getPhaseId(),phase);assertEquals(phase==101?"原配置":"新配置",snap.get("name"));
        }finally{pool.shutdownNow();}
    }
    @Test @EnabledIfEnvironmentVariable(named="PC_BROWSER_FIXTURE",matches="yes")
    void prepareBrowserFixture() {
        for(String table:List.of("user_role","role_permission","role","permission","sys_user")) db.update("DELETE FROM "+table);
        db.update("INSERT INTO role(id,code,name) VALUES (1,'SUPER_ADMIN','隔离管理员')");
        for(String permission:List.of("project:manage","project:create","project:view-all")) {
            db.update("INSERT INTO permission(code,name,type) VALUES(?,?,1)",permission,permission);
            db.update("INSERT INTO role_permission(role_id,permission_code) VALUES(1,?)",permission);
        }
        db.update("INSERT INTO sys_user(id,username,password,is_admin,must_change_password,status) VALUES (1,'配置验收管理员',?,1,0,1),(2,'配置验收普通员工',?,0,0,1)",encoder.encode(System.getenv("PC_TEST_PASSWORD")),encoder.encode(System.getenv("PC_TEST_PASSWORD")));
        db.update("INSERT INTO user_role(user_id,role_id) VALUES (1,1)");
        save("浏览器基线类型",1,List.of(stage(101,"INHERIT",List.of()),stage(102,"NONE",List.of())));
    }
}
