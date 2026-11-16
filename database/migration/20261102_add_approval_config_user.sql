-- =============================================================
-- 审批流配置板块改造（R2 指定具体审批人 + R1 多流程配套字段）
-- 设计依据：docs/design/2026-10-31-审批流配置板块改造-设计文档.md（2.1 数据模型）
-- 内容：
--   1) 新建 approval_config_user 关联表（approval_config 的具体审批人配置，
--      或签语义：任一名具体审批人 approve 即终态）
--   2) application 通用申请主表新增三列：
--      flow_key（多流程标识，存量单为空）、approver_snapshot（提交时审批人快照 JSON）、
--      return_comment（退回修改意见）
-- 幂等：CREATE TABLE IF NOT EXISTS / information_schema 判列后 ALTER，可重复执行
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 审批流具体审批人配置表 ----------------

CREATE TABLE IF NOT EXISTS `approval_config_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `config_id`   BIGINT       NOT NULL COMMENT '审批流配置ID(approval_config.id)',
  `user_id`     BIGINT       NOT NULL COMMENT '审批人用户ID(sys_user.id)',
  `username`    VARCHAR(64)  NOT NULL COMMENT '审批人用户名快照',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_user` (`config_id`, `user_id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流具体审批人配置（R2 指定具体审批人；或签语义）';

-- ---------------- 2. application 新增多流程 / 快照 / 退回意见列 ----------------

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application' AND COLUMN_NAME = 'flow_key'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `application` ADD COLUMN `flow_key` VARCHAR(50) DEFAULT NULL COMMENT ''流程标识（多流程，对应 approval_config.flow_key；存量单为空）'' AFTER `application_type`',
  'SELECT ''flow_key already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application' AND COLUMN_NAME = 'approver_snapshot'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `application` ADD COLUMN `approver_snapshot` JSON DEFAULT NULL COMMENT ''审批人快照 JSON 数组 [{"id":1,"username":"jshz"}]（提交时由流程配置生成；空数组=任意有审批权限的用户可审；存量单为空）'' AFTER `extra_approver`',
  'SELECT ''approver_snapshot already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'application' AND COLUMN_NAME = 'return_comment'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `application` ADD COLUMN `return_comment` VARCHAR(500) DEFAULT NULL COMMENT ''退回修改意见（status=4 时记录）'' AFTER `approve_time`',
  'SELECT ''return_comment already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
