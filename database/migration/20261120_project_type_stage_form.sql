-- =============================================================
-- 20261120_project_type_stage_form.sql
-- 项目类型 + 会前财务资料上传阶段表单（1.0.56 后配套；幂等，可重复执行）
-- 变更一：project 新列 project_type
--   PLATFORM 平台类项目 / DIRECT_PAY 直付类项目 / KEY 重点类项目（必填，服务层校验）
--   原「会议类型」字段 category(ONLINE/OFFLINE) 保留，前端展示名改为「地点」。
-- 变更二：新表 project_stage_form
--   会前财务资料上传等阶段的按项目类型差异化表单数据（form_data 存 JSON），
--   一项目一阶段一份，整包覆盖保存，不做历史版本。
-- 回滚：ALTER TABLE project DROP COLUMN project_type; DROP TABLE project_stage_form;
--       （删表前确认无业务数据依赖）
-- =============================================================
USE `expert_system`;

SET @db = DATABASE();

-- ---------- 1. project 新列（information_schema 守卫，幂等） ----------
SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD COLUMN `project_type` VARCHAR(20) NULL COMMENT ''项目类型：PLATFORM 平台类 / DIRECT_PAY 直付类 / KEY 重点类'' AFTER `category`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND COLUMN_NAME='project_type');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `project` ADD KEY `idx_project_ptype` (`project_type`)',
  'SELECT 1') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project' AND INDEX_NAME='idx_project_ptype');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. 阶段表单表（一项目一阶段一份） ----------
CREATE TABLE IF NOT EXISTS `project_stage_form` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `phase_id` bigint NOT NULL COMMENT '阶段 project_phase.id',
  `form_data` json NOT NULL COMMENT '表单数据（按项目类型差异化字段，key 见前端 STAGE_FORM_SCHEMA）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人 sys_user.id',
  `update_by` bigint DEFAULT NULL COMMENT '最后修改人 sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psf_project_phase` (`project_id`,`phase_id`),
  KEY `idx_psf_phase` (`phase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目阶段表单（会前财务资料上传等按项目类型差异化填写）';

-- ---------- 3. 核对（人工执行） ----------
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA='expert_system' AND TABLE_NAME='project' AND COLUMN_NAME='project_type';
-- SHOW CREATE TABLE project_stage_form;
