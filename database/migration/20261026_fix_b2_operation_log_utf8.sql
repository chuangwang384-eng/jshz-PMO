-- =============================================================
-- B2 迁移中文注释/名称修复（2026-10-26）
-- 背景：迁移脚本经 PowerShell 管道执行时中文字面量被 ASCII 替换为 ?(3F)，
--       来自数据库列的中文（expert_name）未受影响。本脚本修复全部损坏点。
-- 幂等：可重复执行（全部为 UPDATE/MODIFY/DROP-CREATE 重建）。
-- 数据安全：operation_log 仅含 download_log 重放数据，重建无损；
--          expert_change_request 仅改列注释（MODIFY 保持类型与位置）。
-- =============================================================
USE `expert_system`;

-- 1) expert_change_request 两列注释修复（保持列位置）
ALTER TABLE `expert_change_request`
  MODIFY COLUMN `current_assignee` VARCHAR(64) NULL COMMENT '当前审批人（转办后有值；空=未转办，任何有审批权限的用户可审）' AFTER `approve_by`,
  MODIFY COLUMN `extra_approver` VARCHAR(64) NULL COMMENT '加签审批人（或签语义，任一审批即终态）' AFTER `current_assignee`;

-- 2) operation_log 重建（清掉损坏的表/列注释，随后重放历史迁移）
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
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

-- 3) 历史下载迁移重放（与原迁移脚本同款）
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

-- 4) 权限码名称修复（INSERT IGNORE 不更新已存在行，此处显式 UPDATE）
UPDATE `permission` SET `name` = '审批转办' WHERE `method` = 'POST' AND `path` = '/api/approval/requests/*/transfer';
UPDATE `permission` SET `name` = '审批加签' WHERE `method` = 'POST' AND `path` = '/api/approval/requests/*/add-approver';
UPDATE `permission` SET `name` = '可转办/加签用户列表' WHERE `method` = 'GET' AND `path` = '/api/approval/assignable-users';
