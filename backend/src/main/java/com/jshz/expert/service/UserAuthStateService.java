package com.jshz.expert.service;

import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录态用户状态缓存（P1-2）。
 *
 * <p>每次请求都查库代价过高，这里缓存 {@code exists/status/tokenVersion} 三项最小信息，
 * 默认 60 秒过期。管理员停用/删除/改密/改角色时调用 {@link #evict(Long)} 立即失效，
 * 单实例部署下下一次请求即可拒绝旧 Token；多实例部署下最迟一个 TTL 周期生效。</p>
 *
 * <p>缓存不保存密码、手机号等敏感字段；数据库查询异常向上抛出，由过滤器 fail-closed。</p>
 */
@Service
public class UserAuthStateService {

    /** 用户状态快照：不存在时 exists=false，tokenVersion=-1 */
    public record UserAuthState(boolean exists, Integer status, long tokenVersion) {

        public boolean enabled() {
            return exists && status != null && status == 1;
        }

        public static UserAuthState missing() {
            return new UserAuthState(false, null, -1L);
        }
    }

    private record Cached(UserAuthState state, long expiresAt) {
    }

    private final SysUserMapper sysUserMapper;
    private final long ttlMillis;
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();

    public UserAuthStateService(SysUserMapper sysUserMapper,
                                @Value("${expert.auth.state-cache-seconds:60}") long ttlSeconds) {
        this.sysUserMapper = sysUserMapper;
        this.ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
    }

    /**
     * 读取用户状态（带 TTL 缓存）。
     * 数据库异常直接抛出，调用方必须按拒绝处理，不得 fail-open。
     */
    public UserAuthState get(Long userId) {
        if (userId == null) {
            return UserAuthState.missing();
        }
        long now = System.currentTimeMillis();
        Cached cached = cache.get(userId);
        if (cached != null && cached.expiresAt() > now) {
            return cached.state();
        }
        SysUser user = sysUserMapper.selectById(userId);
        UserAuthState state = user == null
                ? UserAuthState.missing()
                : new UserAuthState(true, user.getStatus(),
                        user.getTokenVersion() == null ? 0L : user.getTokenVersion().longValue());
        cache.put(userId, new Cached(state, now + ttlMillis));
        return state;
    }

    /**
     * 仅失效缓存，不修改数据库（删除用户时使用）。
     * 若当前处于事务中，提交后再清一次，避免“提交前被并发请求重新填入旧状态”的竞态。
     */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        cache.remove(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.remove(userId);
                }
            });
        }
    }

    /**
     * 原子自增 token_version 并立即失效缓存。
     * 用于停用/启用/改密/改角色；行不存在时数据库影响 0 行，缓存仍会失效。
     */
    public void bumpAndEvict(Long userId) {
        if (userId == null) {
            return;
        }
        sysUserMapper.bumpTokenVersion(userId);
        evict(userId);
    }
}
