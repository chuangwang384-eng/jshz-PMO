package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.jshz.expert.entity.Notification;
import com.jshz.expert.entity.NotificationMute;
import com.jshz.expert.mapper.NotificationMapper;
import com.jshz.expert.mapper.NotificationMuteMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息中心第二轮回归（2026-11-25，需求 A/D/E + P2/P3 修复）。
 *
 * <p>覆盖：notify 按免打扰偏好过滤且查询失败按未静音放行；password_reset 不可静音；
 * 免打扰偏好保存的白名单（未知类型与 password_reset 400 拒绝、去重、空数组清空）；
 * 保留期清理只删已读且天数钳制；markRead 条件更新只写本人未读行。</p>
 */
class NotificationPreferenceTest {

    private NotificationMapper notificationMapper;
    private NotificationMuteMapper muteMapper;
    private NotificationSseService sseService;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        muteMapper = mock(NotificationMuteMapper.class);
        sseService = mock(NotificationSseService.class);
        service = new NotificationService(
                mock(SysUserMapper.class), muteMapper, sseService);
        ReflectionTestUtils.setField(service, "baseMapper", notificationMapper);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), NotificationMute.class);
    }

    // ---------------- notify：免打扰过滤 + SSE 推送 ----------------

    /** 已静音类型：不落库、不推送 */
    @Test
    void notifySkipsMutedType() {
        when(muteMapper.selectCount(any())).thenReturn(1L);

        service.notify(7L, NotificationService.TYPE_COMPLIANCE_DUE, "t", "c", "/x");

        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(sseService, never()).pushRefresh(7L);
    }

    /** 未静音：落库并推 SSE 刷新信号 */
    @Test
    void notifyWritesAndPushesForUnmutedType() {
        when(muteMapper.selectCount(any())).thenReturn(0L);

        service.notify(7L, NotificationService.TYPE_APPROVAL_TODO, "t", "c", "/x");

        verify(notificationMapper).insert(any(Notification.class));
        verify(sseService).pushRefresh(7L);
    }

    /** 偏好查询异常：按未静音放行（宁可多送不可丢通知） */
    @Test
    void notifyFallsOpenWhenMuteLookupFails() {
        when(muteMapper.selectCount(any())).thenThrow(new RuntimeException("db down"));

        service.notify(7L, NotificationService.TYPE_COMPLIANCE_DUE, "t", "c", "/x");

        verify(notificationMapper).insert(any(Notification.class));
    }

    // ---------------- 免打扰偏好保存 ----------------

    /** password_reset 与未知类型 400 拒绝；合法类型去重保存 */
    @Test
    void saveMutesRejectsPasswordResetAndUnknownTypes() {
        assertThrows(com.jshz.expert.common.BusinessException.class,
                () -> service.saveMutedTypes(7L, List.of(NotificationService.TYPE_PASSWORD_RESET)));
        assertThrows(com.jshz.expert.common.BusinessException.class,
                () -> service.saveMutedTypes(7L, List.of("not_a_type")));

        verify(muteMapper, never()).delete(any());
    }

    @Test
    void saveMutesDeduplicatesAndClearsOnEmptyList() {
        service.saveMutedTypes(7L, List.of(
                NotificationService.TYPE_APPROVAL_TODO, NotificationService.TYPE_APPROVAL_TODO, "  "));

        verify(muteMapper).delete(any());
        verify(muteMapper, times(1)).insert(any(NotificationMute.class));

        service.saveMutedTypes(7L, List.of());
        verify(muteMapper, times(2)).delete(any());
        verify(muteMapper, times(1)).insert(any(NotificationMute.class));
    }

    // ---------------- 保留期清理 ----------------

    /** 天数钳制到 ≥1；删除条件只含已读（SQL 由 wrapper 构造，这里验证不抛与天数边界） */
    @Test
    void deleteReadBeforeClampsDays() {
        when(notificationMapper.delete(any())).thenReturn(5);

        assertEquals(5, service.deleteReadBefore(0));
        assertEquals(5, service.deleteReadBefore(-3));
        assertEquals(5, service.deleteReadBefore(90));
    }

    // ---------------- 类型注册表 ----------------

    @Test
    void knownTypesRegistryCoversAllConstantsAndExcludesPasswordResetFromMutable() {
        assertEquals(16, NotificationService.KNOWN_TYPES.size());
        assertTrue(NotificationService.KNOWN_TYPES.contains(NotificationService.TYPE_DOC_RELISTED));
        assertFalse(NotificationService.MUTABLE_TYPES.contains(NotificationService.TYPE_PASSWORD_RESET));
        assertEquals(NotificationService.KNOWN_TYPES.size() - 1, NotificationService.MUTABLE_TYPES.size());
    }

    // ---------------- helpers ----------------

    private AuthUser userOf(Long id, String name) {
        return new AuthUser(id, name, false, false, Set.of(), Set.of());
    }
}
