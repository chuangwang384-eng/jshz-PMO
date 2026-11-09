-- =============================================================
-- 20261109_project_schedule_stage.sql
-- 项目流程重塑第一阶段「日程上传」（需求/设计见 docs/2026-11-09-项目流程重塑-日程上传）
-- 内容：
--   1. 新表 project_schedule  项目日程文件（current=1 生效份，raw_result 存原始解析 JSON）
--   2. 新表 project_speaker   参会专家（confirmed=0 候选 / 1 正式；expert_id NULL=库外专家）
--   3. project_phase 追加内置阶段 AGENDA_UPLOAD 日程上传（本期唯一阶段，幂等）
--   4. project.type_id 置 NULL（停用项目类型；行数=0 实际无数据，列保留不删）
-- 停用不删：project_type / project_phase(旧种子) / project_type_phase_rel /
--          project_phase_template_rel / project_executor / project_phase_history 全部保留不动。
-- 回滚：DROP TABLE project_schedule; DROP TABLE project_speaker;
--       DELETE FROM project_phase WHERE phase_code='AGENDA_UPLOAD';
-- =============================================================
USE `expert_system`;
SET @db = DATABASE();

-- ---------- 1. project_schedule ----------
CREATE TABLE IF NOT EXISTS `project_schedule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` BIGINT NOT NULL COMMENT '项目 id',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（仅展示，不参与存储路径）',
  `file_path` VARCHAR(255) NOT NULL COMMENT '项目资料库随机文件名',
  `file_size` BIGINT NULL COMMENT '字节',
  `mime_type` VARCHAR(100) NULL,
  `parse_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/DONE/FAILED',
  `parse_message` VARCHAR(500) NULL,
  `raw_result` JSON NULL COMMENT '原始解析输出（回溯用）',
  `is_current` TINYINT NOT NULL DEFAULT 1 COMMENT '1=当前生效份，历史份=0',
  `uploaded_by` BIGINT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_ps_project` (`project_id`, `is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目日程文件';

-- ---------- 2. project_speaker ----------
CREATE TABLE IF NOT EXISTS `project_speaker` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` BIGINT NOT NULL,
  `schedule_id` BIGINT NULL COMMENT '来源日程；手动补录为 NULL',
  `expert_id` BIGINT NULL COMMENT '匹配到的专家档案 id；NULL=库外专家',
  `expert_name` VARCHAR(100) NOT NULL,
  `role` VARCHAR(20) NOT NULL COMMENT 'CHAIR 大会主席/HOST 大会主持/SPEAKER 大会讲者/DISCUSSANT 讨论嘉宾',
  `segment_title` VARCHAR(255) NULL COMMENT '议题/段落',
  `segment_time` VARCHAR(100) NULL COMMENT '时间段文本',
  `confirmed` TINYINT NOT NULL DEFAULT 0 COMMENT '0=解析候选，1=人工确认正式记录',
  `created_by` BIGINT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_spk_project` (`project_id`, `confirmed`),
  KEY `idx_spk_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目参会专家（日程解析结果+人工补录）';

-- ---------- 3. 内置阶段 AGENDA_UPLOAD（幂等；表结构 name/sort/is_active） ----------
SET @name_exists = (SELECT COUNT(*) FROM `project_phase` WHERE `name`='日程上传');
SET @sql = IF(@name_exists=0,
  'INSERT INTO `project_phase` (`name`,`sort`,`is_active`) VALUES (''日程上传'',99,1)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 旧 6 种子阶段（立项/预算/筹备/进行中/结算/已完成）停用：不再可选，数据保留
UPDATE `project_phase` SET `is_active`=0 WHERE `name` IN ('立项','预算','筹备','进行中','结算','已完成');

-- ---------- 4. 停用项目类型：type_id 置 NULL（幂等；当前行数=0） ----------
UPDATE `project` SET `type_id`=NULL WHERE `type_id` IS NOT NULL;

-- ---------- 5. 标记表防重跑提示（本脚本本身幂等，标记表供发布脚本校验） ----------
CREATE TABLE IF NOT EXISTS `mig_20261109_schedule_stage_done` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `done_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
