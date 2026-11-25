package com.jshz.expert.controller;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Application;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ApplicationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批管理列表 type 参数回归（2026-11-25 审批筛选增强）：
 * type 大小写归一化为枚举名后透传服务层；非法 type 按 400 拒绝；分页钳制不变。
 */
class ApplicationControllerTypeFilterTest {

    private ApplicationService service;
    private ApplicationController controller;

    @BeforeEach
    void setUp() {
        service = mock(ApplicationService.class);
        controller = new ApplicationController(service);
        when(service.listRequests(any(), any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(new Page<>(1, 20));
    }

    @Test
    void normalizesTypeAndPassesThrough() {
        AuthUser user = new AuthUser(1L, "approver", false, false, Set.of(), Set.of("approval:audit"));

        controller.listRequests(null, null, null, "expert_modify", 1, 20, user);

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(service).listRequests(isNull(), isNull(), type.capture(), eq(1L), eq(20L), eq(user));
        assertEquals("EXPERT_MODIFY", type.getValue());
    }

    @Test
    void rejectsUnknownType() {
        AuthUser user = new AuthUser(1L, "approver", false, false, Set.of(), Set.of("approval:audit"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.listRequests(null, null, null, "NOT_A_TYPE", 1, 20, user));
        assertEquals(400, ex.getCode());
    }

    @Test
    void clampsOversizedPage() {
        AuthUser user = new AuthUser(1L, "approver", false, false, Set.of(), Set.of("approval:audit"));

        controller.listRequests(null, "1,2", null, null, 0, 500, user);

        verify(service).listRequests(eq(List.of(1, 2)), isNull(), isNull(), eq(1L), eq(100L), eq(user));
    }
}
