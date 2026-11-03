-- ============================================================
-- 20261103_add_compliance_v4_tables.sql
-- 企业合规可复用清单 v4（REQ/DES-COMPLIANCE-V4，20261103）
-- 适用：MySQL 8.x（expert_system）；幂等（IF NOT EXISTS），可重复执行
-- 内容：7 张隔离新表（v3 旧 5 表保留为历史数据源，不修改不删除）
--   compliance_template_version  规范模板版本（发布后不可原地改写，修改=复制新草稿）
--   compliance_template_node     规范节点（分组+细项，最多两级；输入类型：说明/单选/多选/文本）
--   compliance_template_option   单选/多选候选选项
--   compliance_execution         个人执行清单（项目可空关联；幂等键防重复创建）
--   compliance_execution_task    本次执行任务（快照+答案+计划+进度+等待跟进+提醒配置）
--   compliance_execution_event   私人操作事件（备注历史/状态变更/审计，仅本人可见）
--   compliance_execution_reminder 实例级提醒节点（唯一约束幂等，schedule_revision 失效机制）
-- 旧数据迁移按设计 §8 另行批准，本脚本不含数据搬移。
-- 回滚：DROP 七张表（纯新增，无既有数据依赖）。
-- ============================================================
USE `expert_system`;

-- ---------------- 1. 模板版本 ----------------
CREATE TABLE IF NOT EXISTS `compliance_template_version` (
  `id`                          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `document_id`                 BIGINT       NOT NULL COMMENT '来源知识文档',
  `source_document_version_id`  BIGINT       NULL COMMENT '来源文档版本（快照依据）',
  `revision`                    INT          NOT NULL DEFAULT 1 COMMENT '同文档模板修订号',
  `state`                       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
  `owner_user_id`               BIGINT       NOT NULL COMMENT '草稿所有者（上传者）',
  `published_by`                BIGINT       NULL COMMENT '发布人',
  `title`                       VARCHAR(200) NOT NULL COMMENT '模板名称',
  `applicability`               TEXT         NULL COMMENT '适用说明',
  `row_version`                 INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `published_at`                DATETIME     NULL,
  `create_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`                 DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_tv_doc_revision` (`document_id`, `revision`),
  KEY `idx_tv_doc_state` (`document_id`, `state`, `revision`),
  KEY `idx_tv_owner` (`owner_user_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规规范模板版本';

-- ---------------- 2. 模板节点 ----------------
CREATE TABLE IF NOT EXISTS `compliance_template_node` (
  `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  `version_id`         BIGINT       NOT NULL COMMENT '模板版本',
  `parent_id`          BIGINT       NULL COMMENT '父分组节点（同版本；最多两级）',
  `node_code`          VARCHAR(64)  NOT NULL COMMENT '节点稳定标识（同版本唯一）',
  `title`              VARCHAR(200) NOT NULL,
  `stage`              VARCHAR(32)  NULL COMMENT '阶段（沿用 v3 枚举）',
  `input_type`         VARCHAR(24)  NOT NULL DEFAULT 'CHECK' COMMENT 'CHECK 说明/检查项、SINGLE 单选、MULTI 多选、TEXT 文本',
  `instruction`        TEXT         NULL COMMENT '操作说明/说明文字',
  `materials`          TEXT         NULL COMMENT '材料清单',
  `acceptance`         TEXT         NULL COMMENT '完成判定/验收标准',
  `time_rule`          VARCHAR(200) NULL COMMENT '规范时限（原文要求，如 会前7天提交）',
  `required`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必做',
  `source_anchor`      VARCHAR(500) NULL COMMENT '原文引用（页码/章节）',
  `condition_option_id` BIGINT      NULL COMMENT '前置选项（简单分支：选了该选项才适用）',
  `sort`               INT          NOT NULL DEFAULT 0,
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_node_version_code` (`version_id`, `node_code`),
  KEY `idx_node_version_parent` (`version_id`, `parent_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规规范节点（分组+细项）';

-- ---------------- 3. 模板选项 ----------------
CREATE TABLE IF NOT EXISTS `compliance_template_option` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `node_id`     BIGINT       NOT NULL COMMENT '所属单选/多选节点',
  `option_code` VARCHAR(64)  NOT NULL COMMENT '选项稳定标识',
  `label`       VARCHAR(200) NOT NULL COMMENT '选项文案',
  `guidance`    TEXT         NULL COMMENT '该选项适用条件/说明',
  `sort`        INT          NOT NULL DEFAULT 0,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_opt_node_code` (`node_id`, `option_code`),
  KEY `idx_opt_node_sort` (`node_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板候选选项';

-- ---------------- 4. 个人执行清单 ----------------
CREATE TABLE IF NOT EXISTS `compliance_execution` (
  `id`                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  `owner_user_id`         BIGINT       NOT NULL COMMENT '清单所有人（隔离边界）',
  `project_id`            BIGINT       NULL COMMENT '关联系统项目（可空=个人用途）',
  `template_version_id`   BIGINT       NOT NULL COMMENT '固定模板版本',
  `source_document_id`    BIGINT       NOT NULL COMMENT '来源文档（冗余快照）',
  `name`                  VARCHAR(200) NOT NULL COMMENT '清单名称（如 君实·6月·协会与材料）',
  `state`                 VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/COMPLETED/PAUSED/ARCHIVED',
  `start_at`              DATETIME     NULL COMMENT '开始时间（进入待办）',
  `closed_at`             DATETIME     NULL COMMENT '关闭/归档时间',
  `source_kind`           VARCHAR(16)  NOT NULL DEFAULT 'TEMPLATE' COMMENT 'TEMPLATE 模板 / LEGACY 旧数据导入',
  `source_key`            VARCHAR(128) NULL COMMENT '旧数据导入源标识（LEGACY 必填）',
  `idempotency_key`       VARCHAR(64)  NOT NULL COMMENT '创建幂等键（前端 UUID）',
  `row_version`           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_exec_owner_idem` (`owner_user_id`, `idempotency_key`),
  UNIQUE KEY `uk_exec_owner_src` (`owner_user_id`, `source_kind`, `source_key`),
  KEY `idx_exec_owner_state` (`owner_user_id`, `state`, `updated_at`),
  KEY `idx_exec_project` (`project_id`),
  KEY `idx_exec_template` (`template_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人执行清单';

-- ---------------- 5. 执行任务 ----------------
CREATE TABLE IF NOT EXISTS `compliance_execution_task` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `execution_id`     BIGINT       NOT NULL COMMENT '所属清单',
  `source_node_code` VARCHAR(64)  NOT NULL COMMENT '模板节点 code（快照来源）',
  `snapshot`         JSON         NULL COMMENT '创建时节点快照（title/instruction/options/required/time_rule/input_type）',
  `answer`           JSON         NULL COMMENT '本次填写结果（单选code/多选codes/文本）',
  `applicability`    VARCHAR(20)  NOT NULL DEFAULT 'APPLICABLE' COMMENT 'APPLICABLE/NOT_APPLICABLE',
  `due_date`         DATE         NULL COMMENT '本次计划到期日（空=待排期）',
  `priority`         VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  `progress`         VARCHAR(16)  NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'UNPROCESSED/PROCESSING/COMPLETED',
  `note`             TEXT         NULL COMMENT '私人备注（仅本人，≤2000 字）',
  `waiting_reason`   VARCHAR(500) NULL COMMENT '等待跟进：在等什么',
  `next_action`      VARCHAR(500) NULL COMMENT '等待跟进：下一步行动',
  `review_date`      DATE         NULL COMMENT '跟进复查日',
  `remind_enabled`   TINYINT      NOT NULL DEFAULT 1,
  `remind_days`      INT          NOT NULL DEFAULT 3,
  `schedule_revision` INT         NOT NULL DEFAULT 0 COMMENT '提醒计划版本（改期/取消递增失效旧节点）',
  `completed_at`     DATETIME     NULL,
  `row_version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_task_exec_node` (`execution_id`, `source_node_code`),
  KEY `idx_task_exec_progress` (`execution_id`, `progress`),
  KEY `idx_task_progress_due` (`progress`, `due_date`),
  KEY `idx_task_review` (`review_date`, `progress`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人执行任务（快照+答案+计划+进度）';

-- ---------------- 6. 私人操作事件 ----------------
CREATE TABLE IF NOT EXISTS `compliance_execution_event` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `execution_id`   BIGINT      NOT NULL,
  `task_id`        BIGINT      NULL COMMENT '任务级事件（清单级为 NULL）',
  `actor_user_id`  BIGINT      NOT NULL,
  `event_type`     VARCHAR(40) NOT NULL COMMENT 'CREATE/START/PROGRESS/NOTE/DUE/PRIORITY/WAITING/WAIT_CLEAR/COMPLETE/REOPEN/PAUSE/ARCHIVE/REUSE 等',
  `payload`        JSON        NULL COMMENT '字段差异/私人备注历史（仅本人可见，不写公共日志）',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_ev_exec` (`execution_id`, `id`),
  KEY `idx_ev_task` (`task_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清单/任务私人操作事件';

-- ---------------- 7. 实例级提醒节点 ----------------
CREATE TABLE IF NOT EXISTS `compliance_execution_reminder` (
  `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
  `execution_id`      BIGINT      NOT NULL,
  `task_id`           BIGINT      NOT NULL,
  `recipient_user_id` BIGINT      NOT NULL,
  `kind`              VARCHAR(24) NOT NULL COMMENT 'due_before / due_today / review_due',
  `scheduled_at`      DATETIME    NOT NULL,
  `schedule_revision` INT         NOT NULL DEFAULT 0 COMMENT '计划版本（改期/完成/取消递增使旧节点失效）',
  `state`             VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/FAILED/CANCELLED',
  `attempt_count`     INT         NOT NULL DEFAULT 0 COMMENT '重试次数（上限 3）',
  `notification_id`   BIGINT      NULL COMMENT '站内消息 id',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_rem_task_user_kind_sched_rev` (`task_id`, `recipient_user_id`, `kind`, `scheduled_at`, `schedule_revision`),
  KEY `idx_rem_state_sched` (`state`, `scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行任务实例级提醒节点（revision 幂等）';

-- ---------------- 核对 ----------------
-- SHOW TABLES LIKE 'compliance_%';

-- ---------------- 追加（20261103 实施补丁）：提醒表补 sent_time 列 ----------------
SET @st_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'compliance_execution_reminder' AND COLUMN_NAME = 'sent_time');
SET @sql := IF(@st_exists = 0,
  'ALTER TABLE `compliance_execution_reminder` ADD COLUMN `sent_time` DATETIME NULL COMMENT ''实际发送时间'' AFTER `state`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
