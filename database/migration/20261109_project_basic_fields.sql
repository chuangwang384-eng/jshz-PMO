-- =============================================================
-- 20261109_project_basic_fields.sql
-- 项目模块「基本信息」字段扩展（1.0.37.23 配套；幂等，可重复执行）
-- 新字段（Owner 2026-11-09 需求）：
--   team             团队：DEPT1 项目一部 / DEPT2 项目二部 / DEPT3 项目三部（必填，服务层校验）
--   city_id          会议城市 supplier_city.id（非必填，复用供应商城市字典 349 市）
--   meeting_start_date / meeting_end_date  会议举办时间（可单日可多日；单日=两值相同；非必填）
--   client_company / client_name / remark  客户单位 / 客户姓名 / 备注（非必填）
--   协助人：新表 project_assistant（多选，全员可选）
-- 复用既有列：name=会议名称、category=会议类型(ONLINE/OFFLINE)、start_date/end_date=项目起止时间、
--             owner_id=总负责人（口径放开为全员可选，默认创建人）
-- 数据清理（测试数据）由 scripts/apply-migration-20261109-project-rebuild.ps1 负责并带备份，本文件只做加法。
-- 回滚：ALTER TABLE project DROP COLUMN ...; DROP TABLE project_assistant;
-- =============================================================
USE `expert_system`;

-- ---------- 1. project 新列（逐列 information_schema 守卫） ----------
SET @db = DATABASE();

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `team` VARCHAR(20) NULL COMMENT ''团队：DEPT1 项目一部 / DEPT2 项目二部 / DEPT3 项目三部''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='team');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `city_id` BIGINT NULL COMMENT ''会议城市 supplier_city.id''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='city_id');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `meeting_start_date` DATE NULL COMMENT ''会议举办开始日期（单日=与结束同日）''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='meeting_start_date');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `meeting_end_date` DATE NULL COMMENT ''会议举办结束日期''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='meeting_end_date');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `client_company` VARCHAR(200) NULL COMMENT ''客户单位''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='client_company');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `client_name` VARCHAR(100) NULL COMMENT ''客户姓名''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='client_name');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `remark` VARCHAR(500) NULL COMMENT ''备注''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='remark');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. 索引（守卫式；MySQL 无 ADD INDEX IF NOT EXISTS，用 statistics 判断） ----------
SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD KEY `idx_project_team` (`team`)',
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND INDEX_NAME='idx_project_team');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD KEY `idx_project_city` (`city_id`)',
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND INDEX_NAME='idx_project_city');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 3. 协助人表（多选） ----------
CREATE TABLE IF NOT EXISTS `project_assistant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL COMMENT '项目 project.id',
  `user_id` BIGINT NOT NULL COMMENT '协助人 sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pa_project_user` (`project_id`, `user_id`),
  KEY `idx_pa_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目协助人（多选，全员可选）';

-- ---------- 4. 核对（人工执行） ----------
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='project' ORDER BY ORDINAL_POSITION;
-- SHOW CREATE TABLE project_assistant;
