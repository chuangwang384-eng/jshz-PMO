package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工作台快捷访问个性化配置：按账号保存 page.* 卡片标识与顺序，白名单复用布局卡片校验。 */
@Service
public class QuickAccessService {
    private static final int MAX_ITEMS = 10;
    private final DashboardLayoutService layouts;
    private final DashboardReadService reads;
    private final JdbcTemplate db;

    public QuickAccessService(DashboardLayoutService layouts, DashboardReadService reads, JdbcTemplate db) {
        this.layouts = layouts;
        this.reads = reads;
        this.db = db;
    }

    /** 读取当前用户配置（未保存过返回空，前端使用默认入口）；按布局卡片白名单过滤已失去权限的入口，与保存口径对称。 */
    public List<String> load(AuthUser user) {
        reads.requireUser(user);
        return db.queryForList("SELECT item_key FROM user_quick_access WHERE user_id=? ORDER BY sort_order", String.class, user.getUserId())
                .stream().filter(key -> layouts.allowed(user, key)).toList();
    }

    /** 全量覆盖保存；逐项走布局卡片白名单校验，非法项拒绝整单。 */
    @Transactional
    public List<String> save(AuthUser user, List<String> items) {
        reads.requireUser(user);
        if (items == null || items.isEmpty()) throw new BusinessException(400, "快捷访问不能为空");
        if (items.size() > MAX_ITEMS) throw new BusinessException(400, "快捷访问最多 " + MAX_ITEMS + " 个");
        Set<String> seen = new HashSet<>();
        for (String key : items) {
            if (key == null || key.isBlank() || key.length() > 64 || !seen.add(key)) {
                throw new BusinessException(400, "快捷访问标识无效或重复");
            }
            if (!layouts.allowed(user, key)) throw new BusinessException(403, "存在不可用的入口");
        }
        db.update("DELETE FROM user_quick_access WHERE user_id=?", user.getUserId());
        for (int i = 0; i < items.size(); i++) {
            db.update("INSERT INTO user_quick_access(user_id,item_key,sort_order) VALUES (?,?,?)", user.getUserId(), items.get(i), i);
        }
        return load(user);
    }
}
