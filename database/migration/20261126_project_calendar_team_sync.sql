-- =============================================================
-- 20261126_project_calendar_team_sync.sql
-- 项目日历需求（FR-04/FR-05 配套；幂等，可重复执行）
-- 1) 存量 ACTIVE 项目 team 回填：按负责人组织架构部门沿 parent_id 向上找
--    PROJECT_1/2/3 映射 DEPT1/2/3；映射不到置 NULL（8.3 决策：留空）。
--    仅 status=ACTIVE（FR-05「未结项」口径）；COMPLETED/CANCELED/DELETED 不动（8.4 决策）。
-- 2) 补齐存量 ACTIVE 项目缺失的会议举办日期：回填为其项目起止时间（上线前必填校验的前置，8.2）。
-- 3) 可选索引 idx_project_meeting (status, meeting_start_date, meeting_end_date)。
-- 说明：应用层此后由 ProjectTeamSyncService 派生（创建/换负责人/调岗钩子/每日兜底），
--       本回填是一次性对齐，之后以服务派生结果为准。
-- 回滚：team/会议日期可从操作记录恢复（本次生产影响 2 个 ACTIVE 项目，差异已在交接记录留存）；
--       回滚索引：ALTER TABLE project DROP KEY idx_project_meeting;
-- =============================================================
USE `expert_system`;
SET @db = DATABASE();

-- ---------- 1. team 回填（ACTIVE 项目按负责人组织架构派生；找不到映射置 NULL） ----------
-- 2026-11-26 生产实测：ACTIVE 项目仅 2 个（DEPT3×1、DEPT1×1），负责人均有明确部门归属。
UPDATE project p
LEFT JOIN profile_membership m ON m.user_id = p.owner_id
LEFT JOIN profile_department d1 ON d1.id = m.department_id
LEFT JOIN profile_department d2 ON d2.id = d1.parent_id
LEFT JOIN profile_department d3 ON d3.id = d2.parent_id
LEFT JOIN profile_department d4 ON d4.id = d3.parent_id
SET p.team = CASE
  WHEN d1.seed_key IN ('PROJECT_1','PROJECT_2','PROJECT_3') THEN
    CASE d1.seed_key WHEN 'PROJECT_1' THEN 'DEPT1' WHEN 'PROJECT_2' THEN 'DEPT2' ELSE 'DEPT3' END
  WHEN d2.seed_key IN ('PROJECT_1','PROJECT_2','PROJECT_3') THEN
    CASE d2.seed_key WHEN 'PROJECT_1' THEN 'DEPT1' WHEN 'PROJECT_2' THEN 'DEPT2' ELSE 'DEPT3' END
  WHEN d3.seed_key IN ('PROJECT_1','PROJECT_2','PROJECT_3') THEN
    CASE d3.seed_key WHEN 'PROJECT_1' THEN 'DEPT1' WHEN 'PROJECT_2' THEN 'DEPT2' ELSE 'DEPT3' END
  WHEN d4.seed_key IN ('PROJECT_1','PROJECT_2','PROJECT_3') THEN
    CASE d4.seed_key WHEN 'PROJECT_1' THEN 'DEPT1' WHEN 'PROJECT_2' THEN 'DEPT2' ELSE 'DEPT3' END
  ELSE NULL
END
WHERE p.status = 'ACTIVE';

-- ---------- 2. 存量会议日期补齐（ACTIVE 且缺失 → 回填项目起止时间） ----------
UPDATE project
SET meeting_start_date = start_date,
    meeting_end_date   = end_date
WHERE status = 'ACTIVE' AND (meeting_start_date IS NULL OR meeting_end_date IS NULL);

-- ---------- 3. 索引（守卫式；MySQL 无 ADD INDEX IF NOT EXISTS，用 statistics 判断） ----------
SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD KEY `idx_project_meeting` (`status`, `meeting_start_date`, `meeting_end_date`)',
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND INDEX_NAME='idx_project_meeting');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
