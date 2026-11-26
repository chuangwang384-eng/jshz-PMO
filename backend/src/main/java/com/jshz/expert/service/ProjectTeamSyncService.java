package com.jshz.expert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 项目 team 与组织架构自动同步（2026-11-26 项目日历需求 FR-05，需求 C3）：
 * - team 不再由用户手选，由负责人在组织架构中的部门沿 parent_id 向上找到第一个可映射节点派生；
 * - 映射规则：seed_key PROJECT_1/2/3 → DEPT1/2/3；映射不到时 team 置空（8.3 决策：留空，不沿用原值）；
 * - 写入时机：创建项目/变更负责人时派生；员工调岗（profile_membership 变更）后对其名下
 *   status=ACTIVE 的项目重算（COMPLETED/CANCELED/DELETED 历史项目不动，8.4 决策：按当前部门不追溯历史）；
 *   另有每日兜底任务（ProjectTeamSyncTask）。
 * 仅服务内部与调度器调用；提供 resyncForUser 供 ProfileOverviewService 调岗后钩子复用，
 * 禁止各处复制派生 SQL。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectTeamSyncService {

    private static final Map<String, String> SEED_TO_TEAM = Map.of(
            "PROJECT_1", "DEPT1",
            "PROJECT_2", "DEPT2",
            "PROJECT_3", "DEPT3");

    private final JdbcTemplate db;

    /**
     * 派生负责人（ownerId）的团队取值：沿其主部门向上找第一个 PROJECT_1/2/3 节点；
     * 无部门归属或链上无映射节点时返回 null（team 留空）。
     */
    public String deriveTeam(Long ownerId) {
        if (ownerId == null) {
            return null;
        }
        List<Long> deptIds = db.queryForList(
                "SELECT department_id FROM profile_membership WHERE user_id = ?", Long.class, ownerId);
        if (deptIds.isEmpty()) {
            return null;
        }
        Long current = deptIds.get(0);
        // 树约 20 节点、深度 ≤5：向上逐级查 seed_key，visited 防环
        java.util.Set<Long> visited = new java.util.HashSet<>();
        int hops = 0;
        while (current != null && visited.add(current) && hops++ < 20) {
            List<Map<String, Object>> rows = db.queryForList(
                    "SELECT seed_key, parent_id FROM profile_department WHERE id = ?", current);
            if (rows.isEmpty()) {
                return null;
            }
            Object seed = rows.get(0).get("seed_key");
            String team = seed == null ? null : SEED_TO_TEAM.get(String.valueOf(seed));
            if (team != null) {
                return team;
            }
            Object parent = rows.get(0).get("parent_id");
            current = parent == null ? null : ((Number) parent).longValue();
        }
        return null;
    }

    /**
     * 调岗/换负责人后的重算入口：对该用户名下 ACTIVE 项目按派生规则重算 team。
     * 只动 ACTIVE（FR-05「未结项」口径）；返回受影响行数。
     */
    public int resyncForUser(Long userId) {
        String team = deriveTeam(userId);
        return db.update("UPDATE project SET team = ? WHERE owner_id = ? AND status = ?",
                team, userId, ProjectService.STATUS_ACTIVE);
    }

    /** 每日兜底：全部 ACTIVE 项目重算（数据量小，逐条执行，产出差异只记日志）。 */
    public int resyncAll() {
        List<Long> owners = db.queryForList(
                "SELECT DISTINCT owner_id FROM project WHERE status = ? AND owner_id IS NOT NULL",
                Long.class, ProjectService.STATUS_ACTIVE);
        int changed = 0;
        for (Long owner : owners) {
            changed += resyncForUser(owner);
        }
        if (changed > 0) {
            log.info("项目团队同步兜底完成：重算 {} 位负责人的 ACTIVE 项目 team", owners.size());
        }
        return changed;
    }
}
