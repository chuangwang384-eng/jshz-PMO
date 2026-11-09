package com.jshz.expert.service;

import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-2：用户登录态状态短 TTL 缓存单元测试。
 * 纯 Mockito，不启动 Spring、不连接数据库。
 */
@ExtendWith(MockitoExtension.class)
class UserAuthStateServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private SysUser user(int status, int tokenVersion) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(status);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    @Test
    void enabledUserReturnsCurrentVersion() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1, 3));
        UserAuthStateService service = new UserAuthStateService(sysUserMapper, 60);

        UserAuthStateService.UserAuthState state = service.get(1L);

        assertThat(state.exists()).isTrue();
        assertThat(state.enabled()).isTrue();
        assertThat(state.tokenVersion()).isEqualTo(3L);
    }

    @Test
    void disabledOrMissingUserIsRejected() {
        when(sysUserMapper.selectById(2L)).thenReturn(user(0, 1));
        when(sysUserMapper.selectById(3L)).thenReturn(null);

        UserAuthStateService service = new UserAuthStateService(sysUserMapper, 60);

        assertThat(service.get(2L).enabled()).isFalse();
        assertThat(service.get(3L).exists()).isFalse();
        assertThat(service.get(3L).enabled()).isFalse();
        assertThat(service.get(null).exists()).isFalse();
    }

    @Test
    void cacheHitAvoidsRepeatedQueriesAndEvictReloads() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1, 0));
        UserAuthStateService service = new UserAuthStateService(sysUserMapper, 60);

        service.get(1L);
        service.get(1L);
        verify(sysUserMapper, times(1)).selectById(1L);

        service.evict(1L);
        service.get(1L);
        verify(sysUserMapper, times(2)).selectById(1L);
    }

    @Test
    void bumpAndEvictIncrementsVersionAndClearsCache() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1, 4));
        UserAuthStateService service = new UserAuthStateService(sysUserMapper, 60);
        service.get(1L);

        service.bumpAndEvict(1L);
        verify(sysUserMapper, times(1)).bumpTokenVersion(1L);

        when(sysUserMapper.selectById(1L)).thenReturn(user(1, 5));
        assertThat(service.get(1L).tokenVersion()).isEqualTo(5L);
        verify(sysUserMapper, times(2)).selectById(1L);
    }
}
