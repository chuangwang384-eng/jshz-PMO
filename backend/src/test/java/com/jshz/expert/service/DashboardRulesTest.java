package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class DashboardRulesTest {
    final Map<String,Object> legacy=Map.of("phases",List.of(Map.of("phaseId",20,"name","立项"),Map.of("phaseId",90,"name","已完成")));
    @Test void completionRequiresHistoricalProvenanceNotNameOrLastPhase() {
        assertEquals("COMPLETED",DashboardRules.completion("LEGACY_BASELINE",legacy,90));
        assertEquals("INCOMPLETE",DashboardRules.completion("LEGACY_BASELINE",legacy,20));
        assertEquals("UNKNOWN",DashboardRules.completion("CREATED",legacy,90));
        assertEquals("UNKNOWN",DashboardRules.completion("LEGACY_BASELINE",legacy,123));
        assertEquals("UNKNOWN",DashboardRules.completion("LEGACY_BASELINE",Map.of("phases",List.of(Map.of("phaseId",1,"name","结束"))),1));
    }
    @Test void datesIncludeTodayAndSeventhDayButNeverCompleteOrUnknown() {
        LocalDate day=LocalDate.of(2026,11,4);
        assertEquals("OVERDUE",DashboardRules.warning("INCOMPLETE",day.minusDays(1),day));
        assertEquals("DUE_TODAY",DashboardRules.warning("INCOMPLETE",day,day));
        for(int i:List.of(1,7))assertEquals("DUE_SOON",DashboardRules.warning("INCOMPLETE",day.plusDays(i),day));
        assertEquals("NONE",DashboardRules.warning("INCOMPLETE",day.plusDays(8),day));
        assertEquals("NONE",DashboardRules.warning("INCOMPLETE",null,day));
        assertEquals("NONE",DashboardRules.warning("COMPLETED",day.minusDays(1),day));
        assertEquals("UNKNOWN_COMPLETION",DashboardRules.warning("UNKNOWN",day.minusDays(1),day));
    }
    @Test void validatesUnicodeAndLiteralWildcards() {
        assertEquals("😀".repeat(100),DashboardRules.keyword("  "+"😀".repeat(100)+"  "));
        for(String value:List.of(" ","a".repeat(101))) assertThrows(BusinessException.class,()->DashboardRules.keyword(value));
        assertEquals("!%!_\\!!",DashboardRules.literalLike("%_\\!"));
        assertThrows(BusinessException.class,()->DashboardRules.select("a,a",List.of("a","b")));
        assertThrows(BusinessException.class,()->DashboardRules.select("a,",List.of("a","b")));
        assertEquals(List.of("a","b"),DashboardRules.select("b,a",List.of("a","b")));
    }
    @Test void partialFailureAndDeadlineDoNotBecomeEmpty() {
        DashboardTasks tasks=new DashboardTasks();
        try {
            CountDownLatch latch=new CountDownLatch(1);
            Map<String,Supplier<Map<String,Object>>> jobs=new LinkedHashMap<>();
            jobs.put("slow",()->{try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return Map.of("status","success");});
            jobs.put("good",()->Map.of("status","success","data",7));
            jobs.put("bad",()->{throw new IllegalStateException("private SQL");});
            long start=System.nanoTime();var result=tasks.collect(jobs,100);
            assertEquals("MODULE_TIMEOUT",result.get("slow").get("errorCode"));
            assertEquals("success",result.get("good").get("status"));
            assertEquals("error",result.get("bad").get("status"));
            assertFalse(result.toString().contains("private SQL"));
            assertTrue((System.nanoTime()-start)/1_000_000<1000);
        } finally {tasks.close();}
    }
}
