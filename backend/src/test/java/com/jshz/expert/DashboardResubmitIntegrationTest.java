package com.jshz.expert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ApplicationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DASHBOARD_ACCEPTANCE_DB",matches="dashboard_acceptance")
@SpringBootTest(properties={"logging.level.org.springframework.boot.autoconfigure.security=ERROR","expert.compliance.due-enabled=false","expert.compliance.v4-enabled=false","expert.compliance.v4-reminder-enabled=false","expert.wecom.sync-cron-enabled=false","expert.wecom.corp-id=","expert.wecom.contact-secret="})
@Transactional
class DashboardResubmitIntegrationTest {
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13319/dashboard_acceptance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        r.add("spring.datasource.username",()->"root");r.add("spring.datasource.password",()->System.getenv("DASHBOARD_DB_PASSWORD"));
        r.add("expert.jwt.secret",()->UUID.randomUUID().toString()+UUID.randomUUID());
        for(String key:List.of("photo.storage-dir","photo.temp-dir","knowledge.storage-dir","knowledge.preview-dir","customer.storage-dir","supplier.storage-dir","project.storage-dir"))
            r.add("expert."+key,()->"D:/jshz-PMO/.build-dashboard-acceptance/files/test");
    }
    @Autowired JdbcTemplate db;
    @Autowired ApplicationService applications;
    @Autowired ObjectMapper json;
    AuthUser owner=new AuthUser(810001L,"resubmit-fixture",false,false,Set.of(),Set.of());
    @BeforeEach void fixture() throws Exception {
        assertEquals("dashboard_acceptance",db.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(13319,db.queryForObject("SELECT @@port",Integer.class));
        assertTrue(db.queryForObject("SELECT @@datadir",String.class).contains(".build-dashboard-acceptance"));
        Map<String,Object> biz=Map.of("expertId",90001,"expertName","历史申请","reason","旧原因","changes",Map.of("单位","旧单位"),"oldData",Map.of("单位","原始单位"));
        db.update("INSERT INTO application(id,applicant_id,applicant,application_type,status,biz_data,approve_by,return_comment,current_assignee,extra_approver) VALUES (810001,810001,'resubmit-fixture','EXPERT_MODIFY',4,?,'previous','需补充','previous','other')",json.writeValueAsString(biz));
    }
    @Test void textEditsAreAtomicPreserveSnapshotClearAssignmentsAndRejectRepeat() throws Exception {
        applications.resubmit(810001L,Map.of("单位","修订单位"),"修订原因",owner);
        var row=db.queryForMap("SELECT status,biz_data,approve_by,return_comment,current_assignee,extra_approver FROM application WHERE id=810001");
        assertEquals(0,((Number)row.get("status")).intValue());
        var payload=json.readTree(row.get("biz_data").toString());
        assertEquals("修订单位",payload.get("changes").get("单位").asText());
        assertEquals("原始单位",payload.get("oldData").get("单位").asText());
        assertEquals("修订原因",payload.get("reason").asText());
        for(String key:List.of("approve_by","return_comment","current_assignee","extra_approver"))assertNull(row.get(key));
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM approval_record WHERE application_id=810001 AND action='RESUBMIT'",Long.class));
        assertThrows(BusinessException.class,()->applications.resubmit(810001L,Map.of("单位","重复"),"",owner));
    }
    @Test void outsiderAndInvalidSensitiveOrProtectedFieldsCannotChangeState() {
        AuthUser other=new AuthUser(810002L,"outsider",false,false,Set.of(),Set.of());
        assertThrows(BusinessException.class,()->applications.resubmit(810001L,Map.of("单位","越权"),"",other));
        assertThrows(BusinessException.class,()->applications.resubmit(810001L,Map.of("身份证号","123****5678"),"",owner));
        assertThrows(BusinessException.class,()->applications.resubmit(810001L,Map.of("照片","TMP:invalid"),"",owner));
        assertThrows(BusinessException.class,()->applications.resubmit(810001L,Map.of("status","1"),"",owner));
        assertEquals(4,db.queryForObject("SELECT status FROM application WHERE id=810001",Integer.class));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM approval_record WHERE application_id=810001",Long.class));
    }
    @Test void oldBodylessClientKeepsOriginalChanges() throws Exception {
        applications.resubmit(810001L,null,null,owner);
        var payload=json.readTree(db.queryForObject("SELECT biz_data FROM application WHERE id=810001",String.class));
        assertEquals("旧单位",payload.get("changes").get("单位").asText());
    }
}
