-- =============================================================
-- 20261116_budget_fee_scene.sql
-- 收费标准分线上/线下场景（Owner 2026-11-16：项目配置页分两个页面，线上项目点选收费项生成报价单）
-- 内容（幂等，可重复执行）：
--   1. project_budget_fee_standard 加列 scene VARCHAR(20)（ONLINE/OFFLINE）
--   2. 存量 13 行种子回填 scene='OFFLINE'（原种子均为线下口径）
-- 回滚：ALTER TABLE project_budget_fee_standard DROP COLUMN scene;
-- =============================================================
USE `expert_system`;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_budget_fee_standard' AND COLUMN_NAME = 'scene');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `project_budget_fee_standard` ADD COLUMN `scene` VARCHAR(20) NOT NULL DEFAULT ''OFFLINE'' COMMENT ''适用大类：ONLINE 线上 / OFFLINE 线下'' AFTER `name`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE `project_budget_fee_standard` SET `scene` = 'OFFLINE' WHERE `scene` IS NULL OR `scene` = '';

-- 标记表
CREATE TABLE IF NOT EXISTS `mig_20261116_budget_fee_scene_done` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `done_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
