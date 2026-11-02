-- ============================================================
-- 20261102_project_config_v2.sql
-- 项目配置板块改造 v2（配套《项目配置板块改造需求文档 v2.0》《设计文档 v1.0》）
-- 适用：MySQL 8.x（expert_system）
-- 幂等：建表 IF NOT EXISTS；加列/改索引经 information_schema 判断后执行；INSERT IGNORE；可重复执行
-- 内容：
--   1) 新建 project_type_phase_rel（类型→阶段序列）
--   2) project_phase_template_rel 加 type_id（0=全局兜底）+ 唯一键调整
--   3) 清理 2 条死规则（config/rel POST/DELETE；templates PUT/DELETE 两条保留——本次接口补全后生效）
--   4) 补 3 条 project:manage 规则（类型阶段序列保存/类型阶段绑定保存/全局绑定保存）
-- 回滚：见文件尾注释（反向执行；业务数据无不可逆转换）
-- ============================================================

-- ---------------- 1. 类型-阶段序列表 ----------------
CREATE TABLE IF NOT EXISTS `project_type_phase_rel` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type_id`     BIGINT NOT NULL COMMENT '项目类型ID（project_type.id）',
  `phase_id`    BIGINT NOT NULL COMMENT '流程阶段ID（project_phase.id）',
  `sort`        INT NOT NULL DEFAULT 0 COMMENT '阶段顺序（小在前）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_phase` (`type_id`, `phase_id`),
  KEY `idx_type_id` (`type_id`),
  KEY `idx_phase_id` (`phase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目类型-流程阶段关联（类型自定义阶段序列）';

-- ---------------- 2. project_phase_template_rel 加类型维度 ----------------
-- 2a. 加列 type_id（0=全局兜底），存量行自动落 0，无需搬数
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_phase_template_rel' AND COLUMN_NAME = 'type_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `project_phase_template_rel` ADD COLUMN `type_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''项目类型ID，0=全局兜底绑定'' AFTER `template_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2b. 唯一键 (phase_id, template_id) -> (type_id, phase_id, template_id)
SET @old_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_phase_template_rel' AND INDEX_NAME = 'uk_phase_template');
SET @sql := IF(@old_idx > 0,
  'ALTER TABLE `project_phase_template_rel` DROP INDEX `uk_phase_template`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_phase_template_rel' AND INDEX_NAME = 'uk_type_phase_template');
SET @sql := IF(@new_idx = 0,
  'ALTER TABLE `project_phase_template_rel` ADD UNIQUE KEY `uk_type_phase_template` (`type_id`, `phase_id`, `template_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------- 3. 清理死规则（接口从未存在的 2 条；templates PUT/DELETE 本次接口补全后保留生效） ----------------
DELETE FROM `permission`
WHERE `type` = 2 AND (
  (`method` = 'POST'   AND `path` = '/api/project/config/rel') OR
  (`method` = 'DELETE' AND `path` = '/api/project/config/rel/*')
);

-- ---------------- 4. 补权限规则（均 project:manage；templates PUT/DELETE 已在 20261027 存量，勿重复插） ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('project:manage', '类型阶段序列保存',     2, 'PUT', '/api/project/config/types/*/phases'),
('project:manage', '类型阶段模板绑定保存', 2, 'PUT', '/api/project/config/types/*/phases/*/templates'),
('project:manage', '阶段模板绑定保存',     2, 'PUT', '/api/project/config/phases/*/templates');

-- ---------------- 5. 核对（人工执行，非脚本内容） ----------------
-- SELECT method,path,name FROM permission WHERE path LIKE '/api/project/config%' ORDER BY method,path;
-- SHOW COLUMNS FROM project_phase_template_rel LIKE 'type_id';
-- SHOW INDEX FROM project_phase_template_rel WHERE Key_name='uk_type_phase_template';

-- ============================================================
-- 回滚预案（反向执行；存量业务数据语义不变，无需搬数回滚）：
--   DELETE FROM permission WHERE type=2 AND (
--     (method='PUT' AND path='/api/project/config/types/*/phases') OR
--     (method='PUT' AND path='/api/project/config/types/*/phases/*/templates') OR
--     (method='PUT' AND path='/api/project/config/phases/*/templates'));
--   ALTER TABLE project_phase_template_rel DROP INDEX uk_type_phase_template;
--   ALTER TABLE project_phase_template_rel ADD UNIQUE KEY uk_phase_template (phase_id, template_id);
--     -- 执行前需先 DELETE type_id<>0 的行，否则唯一键冲突
--   ALTER TABLE project_phase_template_rel DROP COLUMN type_id;
--   DROP TABLE IF EXISTS project_type_phase_rel;
--   并回退应用到不含本次功能的前一版本 jar/dist。
-- ============================================================
