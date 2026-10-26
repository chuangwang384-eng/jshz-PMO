-- =============================================================
-- B2 审批转办/加签 + 通用用户操作日志（用户操作日志改版）
-- 设计依据：docs/superpowers/specs/2026-10-26-b2-transfer-operation-log-design.md
-- 幂等：可重复执行（information_schema 判列 / CREATE TABLE IF NOT EXISTS / INSERT IGNORE）
-- 内容：
--   1) expert_change_request 加列 current_assignee / extra_approver
--   2) 新表 operation_log（通用敏感操作日志；source_id 标记 download_log 来源，唯一键防重复迁移）
--   3) 历史 download_log 迁移为 operation_log（action_type=DOWNLOAD，INSERT IGNORE 幂等）
--   4) 接口权限码：转办 / 加签 / 可转办用户列表（approval:audit，复用一码多路径）
-- 语义确认（2026-10-26 用户拍板）：
--   加签 = 或签：原审批人与被加签人任一 approve 即通过、任一 reject 即驳回；
--   转办/加签目标用户必须拥有 approval:audit 权限码。
-- =============================================================
USE `expert_system`;

-- ---------------- 1. expert_change_request 加列 ----------------
-- current_assignee：转办后有值；为空 = 未转办，任何拥有 approval:audit 的用户可审批（向后兼容）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'expert_change_request'
    AND COLUMN_NAME = 'current_assignee'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `expert_change_request` ADD COLUMN `current_assignee` VARCHAR(64) NULL COMMENT ''当前审批人（转办后有值；空=未转办，任何有审批权限的用户可审）'' AFTER `approve_by`',
  'SELECT ''current_assignee already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- extra_approver：加签审批人（或签：与原审批人任一审批即终态；暂为单人，未来多级加签可改 JSON）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'expert_change_request'
    AND COLUMN_NAME = 'extra_approver'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `expert_change_request` ADD COLUMN `extra_approver` VARCHAR(64) NULL COMMENT ''加签审批人（或签语义，任一审批即终态）'' AFTER `current_assignee`',
  'SELECT ''extra_approver already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 2. 通用操作日志表 ----------------
-- 覆盖：下载/导出（DOWNLOAD）、专家/文档写操作、登录/登出/改密/角色权限变更等敏感操作
-- source_id：历史迁移自 download_log 时记录来源行 id（配合唯一键防重复迁移）；新写入为 NULL

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL COMMENT '操作人 sys_user.id（可空，如历史迁移缺失）',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '操作人用户名',
  `action_type` VARCHAR(32) NOT NULL COMMENT '操作类型枚举：DOWNLOAD/LOGIN/LOGOUT/CHANGE_PASSWORD/RESET_PASSWORD/CREATE_USER/UPDATE_USER/DELETE_USER/ASSIGN_ROLES/ROLE_PERMISSION/CREATE_EXPERT/UPDATE_EXPERT/DELETE_EXPERT/IMPORT_EXPERT/CREATE_DOC/UPDATE_DOC/OFF_SHELF_DOC/DELETE_DOC',
  `action_desc` VARCHAR(500) DEFAULT NULL COMMENT '操作描述（含操作对象中文名）',
  `target_type` VARCHAR(32) DEFAULT NULL COMMENT '对象类型：USER/ROLE/EXPERT/DOC 等',
  `target_id` VARCHAR(64) DEFAULT NULL COMMENT '对象标识（id 或用户名）',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
  `source_id` BIGINT DEFAULT NULL COMMENT '来源记录ID（download_log.id，历史迁移标记；新写入为 NULL）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source` (`action_type`, `source_id`),
  KEY `idx_username` (`username`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户敏感操作日志表';

-- ---------------- 3. 历史下载留痕迁移（幂等） ----------------
-- download_log -> operation_log（action_type=DOWNLOAD）；重复执行由 uk_source(action_type, source_id) + INSERT IGNORE 防重
-- desc 迁移口径：专家名 / 文档标题 / 全量数据（与改造后页面展示一致）

INSERT IGNORE INTO `operation_log`
  (`user_id`, `username`, `action_type`, `action_desc`, `target_type`, `target_id`, `ip`, `source_id`, `create_time`)
SELECT
  `user_id`,
  `username`,
  'DOWNLOAD',
  COALESCE(NULLIF(`expert_name`, ''), '全量数据'),
  CASE WHEN `doc_id` IS NOT NULL THEN 'DOC' ELSE 'EXPERT' END,
  COALESCE(CAST(`doc_id` AS CHAR), CAST(`expert_id` AS CHAR)),
  `ip`,
  `id`,
  `download_time`
FROM `download_log`;

-- ---------------- 4. 接口权限码（approval:audit 复用一码多路径） ----------------
-- GET /api/audit/operations 复用既有规则 audit:view（GET /api/audit/**），无需新增

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('approval:audit', '审批转办', 2, 'POST', '/api/approval/requests/*/transfer'),
('approval:audit', '审批加签', 2, 'POST', '/api/approval/requests/*/add-approver'),
('approval:audit', '可转办/加签用户列表', 2, 'GET', '/api/approval/assignable-users');
