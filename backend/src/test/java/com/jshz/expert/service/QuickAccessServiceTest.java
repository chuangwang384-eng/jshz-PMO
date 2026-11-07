package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 快捷访问配置校验逻辑：白名单复用布局卡片校验，非法项拒绝整单。 */
class QuickAccessServiceTest {
    AuthUser user = new AuthUser(501L, "fixture", false, false, Set.of(), Set.of());

    QuickAccessService service(JdbcTemplate db, boolean pageProjectAllowed) {
        DashboardReadService reads = mock(DashboardReadService.class);
        doNothing().when(reads).requireUser(any());
        DashboardLayoutService layouts = mock(DashboardLayoutService.class);
        when(layouts.allowed(eq(user), eq("page.project"))).thenReturn(pageProjectAllowed);
        when(layouts.allowed(eq(user), eq("page.knowledge"))).thenReturn(true);
        return new QuickAccessService(layouts, reads, db);
    }

    @Test void saveRejectsNullEmptyAndOversize() {
        QuickAccessService service = service(mock(JdbcTemplate.class), true);
        assertThrows(BusinessException.class, () -> service.save(user, null));
        assertThrows(BusinessException.class, () -> service.save(user, List.of()));
        assertThrows(BusinessException.class, () -> service.save(user,
                List.of("page.knowledge","page.knowledge")));
        assertThrows(BusinessException.class, () -> service.save(user,
                Collections.nCopies(11, "page.knowledge")));
        assertThrows(BusinessException.class, () -> service.save(user, List.of(" ")));
        assertThrows(BusinessException.class, () -> service.save(user, List.of("x".repeat(65))));
    }

    @Test void saveRejectsDisallowedKeyAndDoesNotWrite() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        QuickAccessService service = service(db, false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(user, List.of("page.project")));
        assertEquals(403, ex.getCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void saveWritesFullReplaceInOrder() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("page.knowledge", "page.project"));
        QuickAccessService service = service(db, true);
        List<String> result = service.save(user, List.of("page.project", "page.knowledge"));
        assertEquals(List.of("page.knowledge", "page.project"), result);
        verify(db).update(eq("DELETE FROM user_quick_access WHERE user_id=?"), eq(501L));
        verify(db).update(eq("INSERT INTO user_quick_access(user_id,item_key,sort_order) VALUES (?,?,?)"),
                eq(501L), eq("page.project"), eq(0));
        verify(db).update(eq("INSERT INTO user_quick_access(user_id,item_key,sort_order) VALUES (?,?,?)"),
                eq(501L), eq("page.knowledge"), eq(1));
    }

    @Test void loadReturnsOrderedKeys() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(anyString(), eq(String.class), eq(501L))).thenReturn(List.of("page.project"));
        assertTrue(service(db, true).load(user).contains("page.project"));
    }
}
