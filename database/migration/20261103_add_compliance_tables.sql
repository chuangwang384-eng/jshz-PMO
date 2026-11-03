-- ============================================================
-- 20261103_add_compliance_tables.sql
-- 企业合规板块「个人关注驱动待办」改造（REQ/DES v3.0，20261102）
-- 适用：MySQL 8.x（expert_system）；幂等（IF NOT EXISTS / 条件加列 / 条件建索引），可重复执行
-- 内容：
--   1) compliance_item 合规事项主表（含 v3 跨版本字段 item_code/document_version_id/previous_item_id/change_type）
--   2) compliance_item_assignee 事项责任人（多人）
--   3) compliance_user_progress 个人进度/提醒/备注（含 v3 block_reason）
--   4) compliance_reminder_log 提醒记录（唯一约束幂等）
--   5) compliance_item_history 事项修改历史
-- 收藏复用 knowledge_favorite，不新增关注表。
-- 回滚：DROP 五张表（纯新增，无既有数据依赖）。
-- ============================================================
USE `expert_system`;

-- ---------------- 1. compliance_item ----------------
CREATE TABLE IF NOT EXISTS `compliance_item` (
  `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  `item_code`           VARCHAR(64)  NOT NULL COMMENT '跨版本稳定标识（首次生成后不变）',
  `document_id`         BIGINT       NOT NULL COMMENT '所属文档 knowledge_document.id',
  `document_version_id` BIGINT       NULL     COMMENT '所属文档版本 knowledge_doc_version.id',
  `previous_item_id`    BIGINT       NULL     COMMENT '上一版本同一事项 id（版本血缘）',
  `change_type`         VARCHAR(16)  NOT NULL DEFAULT '不变' COMMENT '新增/修改/删除/不变',
  `title`               VARCHAR(200) NOT NULL COMMENT '事项标题',
  `content`             TEXT         NULL COMMENT '要点内容',
  `stage`               VARCHAR(32)  NOT NULL DEFAULT 'OTHER' COMMENT 'INITIATION/FINANCE/EXECUTION/VERIFICATION/SPECIAL/OTHER',
  `time_note`           VARCHAR(200) NULL COMMENT '时间要求（文本）',
  `due_date`            DATE         NULL COMMENT '到期日期',
  `priority`            VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/CONFIRMING/COMPLETED（全局四态，仅维护人可改）',
  `is_draft`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1=待确认草稿 0=已发布',
  `pending_confirm`     TINYINT      NOT NULL DEFAULT 0 COMMENT '跨版本匹配待维护人确认（相似度档）',
  `project_id`          BIGINT       NULL COMMENT '预留弱关联 project.id',
  `remark`              VARCHAR(500) NULL COMMENT '全局备注',
  `last_due_notified`   DATE         NULL COMMENT '全局责任人到期提醒最近推送日期',
  `update_by_name`      VARCHAR(50)  NULL COMMENT '最后更新人用户名',
  `create_by_name`      VARCHAR(50)  NULL COMMENT '创建人用户名',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_item_code` (`item_code`),
  KEY `idx_ci_document` (`document_id`),
  KEY `idx_ci_status_due` (`status`, `due_date`),
  KEY `idx_ci_stage` (`stage`),
  KEY `idx_ci_draft` (`is_draft`, `document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规事项主表';

-- ---------------- 2. compliance_item_assignee ----------------
CREATE TABLE IF NOT EXISTS `compliance_item_assignee` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `item_id`     BIGINT NOT NULL,
  `user_id`     BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_item_user` (`item_id`, `user_id`),
  KEY `idx_assignee_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事项责任人关联（多人）';

-- ---------------- 3. compliance_user_progress ----------------
CREATE TABLE IF NOT EXISTS `compliance_user_progress` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `item_id`         BIGINT       NOT NULL,
  `user_id`         BIGINT       NOT NULL,
  `progress_status` VARCHAR(16)  NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'UNPROCESSED/PROCESSING/COMPLETED（个人三态，仅本人）',
  `block_reason`    VARCHAR(200) NULL COMMENT '阻塞原因（预设或自定义）',
  `remark`          VARCHAR(500) NULL COMMENT '个人备注（仅本人）',
  `remind_enabled`  TINYINT      NOT NULL DEFAULT 1 COMMENT '个人提醒开关',
  `remind_days`     INT          NOT NULL DEFAULT 3 COMMENT '提醒提前天数（1-30）',
  `last_due_notified` DATE       NULL COMMENT '个人提醒最近推送日期',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_item` (`user_id`, `item_id`),
  KEY `idx_up_user_status` (`user_id`, `progress_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人事项进度/提醒/备注（仅本人可见）';

-- ---------------- 4. compliance_reminder_log ----------------
CREATE TABLE IF NOT EXISTS `compliance_reminder_log` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`        BIGINT      NOT NULL,
  `item_id`        BIGINT      NOT NULL,
  `reminder_type`  VARCHAR(32) NOT NULL COMMENT 'due_before / due_today / overdue / assignee_due / done_notify 等',
  `scheduled_time` DATETIME    NOT NULL COMMENT '计划提醒时间（自然日）',
  `sent_time`      DATETIME    NULL,
  `send_status`    VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/SKIPPED',
  `retry_count`    INT         NOT NULL DEFAULT 0 COMMENT '重试次数（上限 3）',
  `workday_only`   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否仅工作日（预留，下轮实现）',
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_reminder_unique` (`user_id`, `item_id`, `reminder_type`, `scheduled_time`),
  KEY `idx_reminder_status` (`send_status`, `scheduled_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规提醒记录（唯一约束幂等）';

-- ---------------- 5. compliance_item_history ----------------
CREATE TABLE IF NOT EXISTS `compliance_item_history` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `item_id`        BIGINT       NOT NULL,
  `field_name`     VARCHAR(32)  NOT NULL COMMENT '变更字段或审计动作（草稿发布/下架/恢复/删除/匹配确认/责任人等）',
  `old_value`      VARCHAR(500) NULL,
  `new_value`      VARCHAR(500) NULL,
  `operator_id`    BIGINT       NULL,
  `operator_name`  VARCHAR(50)  NULL,
  `operation_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `change_reason`  VARCHAR(200) NULL COMMENT '变更原因（可选填）',
  KEY `idx_history_item` (`item_id`, `operation_time`),
  KEY `idx_history_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事项修改历史（字段变更+审计动作）';

-- ---------------- 核对 ----------------
-- SHOW TABLES LIKE 'compliance%';
