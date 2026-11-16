package com.jshz.expert.service;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ComplianceUserMode;
import com.jshz.expert.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Missing row = V3. One user lock serializes cutover, writes and both schedulers. */
@Service @RequiredArgsConstructor
public class ComplianceRolloutService {
    @org.springframework.beans.factory.annotation.Value("${expert.compliance.v4-enabled:false}") private boolean enabled;
    private final ComplianceUserModeMapper modes;
    private final SysUserMapper users;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final ComplianceLegacyService legacy;
    private volatile Boolean modeTableReady;
    private boolean hasModeTable() {
        if (modeTableReady == null) {
            synchronized (this) {
                if (modeTableReady == null) modeTableReady = db.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='compliance_user_mode'", Integer.class) == 1;
            }
        }
        return modeTableReady;
    }
    public boolean isV4(Long userId) {
        // A feature shutdown must never silently reactivate the user's V3 writes/reminders.
        if (!hasModeTable()) return false;
        ComplianceUserMode row = modes.selectById(userId);
        return row != null && "V4".equals(row.getMode());
    }
    public boolean isEnabled() { return enabled && hasModeTable(); }
    public void lockUser(Long userId) {
        var user = users.lockById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(401, "用户不可用");
    }
    public void requireV4(Long userId) {
        lockUser(userId);
        if (!isEnabled() || !isV4(userId)) throw new BusinessException(409, "新版未开放或尚未确认启用");
    }
    public void requireV3(Long userId) {
        lockUser(userId);
        if (isV4(userId)) throw new BusinessException(409, "旧版已转为只读历史，请在新版清单操作");
    }
    @Transactional(rollbackFor=Exception.class)
    public void enable(Long userId) {
        if (!isEnabled()) throw new BusinessException(409, "新版尚未开放，请联系维护人");
        lockUser(userId);
        if (isV4(userId)) return;
        ComplianceUserMode row = new ComplianceUserMode(); row.setUserId(userId); row.setMode("V4"); row.setSwitchedAt(LocalDateTime.now());
        row.setLegacySnapshot(legacy.capture(userId));
        if (modes.selectById(userId) == null) modes.insert(row); else modes.updateById(row);
    }
}
