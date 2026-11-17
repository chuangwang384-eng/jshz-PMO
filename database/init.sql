-- ============================================================
-- 专家库系统 数据库初始化脚本（生产结构基线）
-- 生成时间: 2026-11-12 17:21:18；2026-11-16 补齐 4 张表（AI 助手 1 + 会议预算 3，见文末第四节）
-- 来源: 生产库 expert_system 实际结构（纳入 85 张业务/结构表；排除 16 张历史备份/迁移标记表）
-- 适用: MySQL 8.x
--
-- 组成:
--   1) 库与表结构：CREATE TABLE IF NOT EXISTS（不含 DROP，不含 AUTO_INCREMENT 计数器，可重复执行）
--   2) 字典与权限种子：role / permission / role_permission / project_type / project_phase /
--      project_type_phase_rel / project_phase_template_rel / project_template / supplier_type /
--      supplier_city / customer_relation_type / approval_config（INSERT IGNORE）
--   3) 初始管理员 admin（密码 111111，部署后立即改密）
--
-- 不含: 业务数据（专家/项目/客户/文档/日志）、用户口令哈希、历史备份表（*_bak_*）
-- 迁移版本: schema_migration 表随结构创建；历史 63 个脚本（截至 20261116）已登记，后续变更一律
--           新增 database\migration\<yyyyMMdd>_*.sql 并用 scripts\db-migrate.ps1 -Apply 执行登记
-- 历史: 2026-11-12 之前的 init.sql 仅 6 张表，已与生产结构严重脱节，本次按实际库重建
-- ============================================================

CREATE DATABASE IF NOT EXISTS `expert_system`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `expert_system`;

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 一、表结构（81 张）
-- ------------------------------------------------------------

/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '公告标题',
  `content` mediumtext NOT NULL COMMENT '富文本正文（XSS 白名单清洗后入库）',
  `effective_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间（到达后才在首页/生效列表展示）',
  `expire_time` datetime DEFAULT NULL COMMENT '失效时间（空=永久有效）',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '置顶标记：0 否 / 1 是',
  `status` varchar(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '状态：ONLINE 上架 / OFFLINE 下架',
  `creator_id` bigint DEFAULT NULL COMMENT '发布人 sys_user.id',
  `creator_name` varchar(50) DEFAULT NULL COMMENT '发布人用户名快照',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_announce_status_time` (`status`,`effective_time`,`create_time`),
  KEY `idx_announce_top` (`is_top`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统公告主表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `announcement_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `announcement_id` bigint NOT NULL COMMENT '公告 announcement.id',
  `user_id` bigint NOT NULL COMMENT '已读用户 sys_user.id',
  `read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`,`user_id`),
  KEY `idx_read_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告已读记录（唯一联合键保证幂等）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人 sys_user.id（存量迁移按用户名回查，可空）',
  `applicant` varchar(64) NOT NULL COMMENT '申请人用户名（兼容旧数据语义）',
  `application_type` varchar(32) NOT NULL COMMENT '类型编码：EXPERT_MODIFY / EXPERT_CREATE（未来扩展 LEAVE 等）',
  `flow_key` varchar(50) DEFAULT NULL COMMENT '流程标识（多流程，对应 approval_config.flow_key；存量单为空）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 退回修改',
  `biz_data` json DEFAULT NULL COMMENT '业务详情 JSON：专家申请 {expertId, expertName, changes, reason, templateId}（照片 TMP: 前缀语义保留）',
  `approve_by` varchar(64) DEFAULT NULL COMMENT '审批人用户名',
  `approve_comment` varchar(500) DEFAULT NULL COMMENT '审批意见（驳回必填，通过可空）',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `return_comment` varchar(500) DEFAULT NULL COMMENT '退回修改意见（status=4 时记录）',
  `current_assignee` varchar(64) DEFAULT NULL COMMENT '当前审批人（B2 转办后有值；空=未转办，任何有审批权限的用户可审）',
  `extra_approver` varchar(64) DEFAULT NULL COMMENT '加签审批人（B2 或签语义，任一审批即终态）',
  `approver_snapshot` json DEFAULT NULL COMMENT '审批人快照 JSON 数组 [{"id":1,"username":"jshz"}]（提交时由流程配置生成；空数组=任意有审批权限的用户可审；存量单为空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_applicant` (`applicant`),
  KEY `idx_applicant_id` (`applicant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用申请主表（我的申请，各业务线申请聚合）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `approval_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_key` varchar(50) NOT NULL COMMENT '流程标识',
  `flow_name` varchar(100) NOT NULL DEFAULT '专家信息修改审批' COMMENT '流程名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用审批流（1启用/0停用）',
  `approver_role` varchar(50) NOT NULL DEFAULT 'ADMIN' COMMENT '审批人角色',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `flow_key` (`flow_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流配置';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `approval_config_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_id` bigint NOT NULL COMMENT '审批流配置ID(approval_config.id)',
  `user_id` bigint NOT NULL COMMENT '审批人用户ID(sys_user.id)',
  `username` varchar(64) NOT NULL COMMENT '审批人用户名快照',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_user` (`config_id`,`user_id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流具体审批人配置（R2 指定具体审批人；或签语义）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL COMMENT '审批单ID',
  `application_id` bigint DEFAULT NULL COMMENT '关联通用申请 application.id（新链路写入；存量按同 id 回填）',
  `node_name` varchar(100) NOT NULL COMMENT '节点名称',
  `action` varchar(20) NOT NULL COMMENT '动作：SUBMIT/APPROVE/REJECT/WITHDRAW/TRANSFER/ADD_APPROVER/RETURN/RESUBMIT',
  `operator` varchar(100) NOT NULL COMMENT '操作人',
  `comment` varchar(500) DEFAULT NULL COMMENT '说明/意见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流进度记录';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `approval_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_name` varchar(100) NOT NULL COMMENT '模板名称（唯一）',
  `fields` text NOT NULL COMMENT '模板字段 JSON 数组（中文键，⊆ 专家可编辑白名单+照片字段）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1 启用 / 0 停用（停用后不可用于新申请）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批申请字段模板表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_user_id` bigint NOT NULL COMMENT '清单所有人（隔离边界）',
  `project_id` bigint DEFAULT NULL COMMENT '关联系统项目（可空=个人用途）',
  `template_version_id` bigint NOT NULL COMMENT '固定模板版本',
  `source_document_id` bigint NOT NULL COMMENT '来源文档（冗余快照）',
  `name` varchar(200) NOT NULL COMMENT '清单名称（如 君实·6月·协会与材料）',
  `state` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/COMPLETED/PAUSED/ARCHIVED',
  `start_at` datetime DEFAULT NULL COMMENT '开始时间（进入待办）',
  `closed_at` datetime DEFAULT NULL COMMENT '关闭/归档时间',
  `source_kind` varchar(16) NOT NULL DEFAULT 'TEMPLATE' COMMENT 'TEMPLATE 模板 / LEGACY 旧数据导入',
  `source_key` varchar(128) DEFAULT NULL COMMENT '旧数据导入源标识（LEGACY 必填）',
  `idempotency_key` varchar(64) NOT NULL COMMENT '创建幂等键（前端 UUID）',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exec_owner_idem` (`owner_user_id`,`idempotency_key`),
  UNIQUE KEY `uk_exec_owner_src` (`owner_user_id`,`source_kind`,`source_key`),
  KEY `idx_exec_owner_state` (`owner_user_id`,`state`,`updated_at`),
  KEY `idx_exec_project` (`project_id`),
  KEY `idx_exec_template` (`template_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人执行清单';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_execution_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL COMMENT '任务级事件（清单级为 NULL）',
  `actor_user_id` bigint NOT NULL,
  `event_type` varchar(40) NOT NULL COMMENT 'CREATE/START/PROGRESS/NOTE/DUE/PRIORITY/WAITING/WAIT_CLEAR/COMPLETE/REOPEN/PAUSE/ARCHIVE/REUSE 等',
  `payload` json DEFAULT NULL COMMENT '字段差异/私人备注历史（仅本人可见，不写公共日志）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ev_exec` (`execution_id`,`id`),
  KEY `idx_ev_task` (`task_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清单/任务私人操作事件';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_execution_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `recipient_user_id` bigint NOT NULL,
  `kind` varchar(24) NOT NULL COMMENT 'due_before / due_today / review_due',
  `scheduled_at` datetime NOT NULL,
  `schedule_revision` int NOT NULL DEFAULT '0' COMMENT '计划版本（改期/完成/取消递增使旧节点失效）',
  `state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/FAILED/CANCELLED',
  `sent_time` datetime DEFAULT NULL COMMENT '实际发送时间',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '重试次数（上限 3）',
  `notification_id` bigint DEFAULT NULL COMMENT '站内消息 id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rem_task_user_kind_sched_rev` (`task_id`,`recipient_user_id`,`kind`,`scheduled_at`,`schedule_revision`),
  KEY `idx_rem_state_sched` (`state`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行任务实例级提醒节点（revision 幂等）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_execution_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL COMMENT '所属清单',
  `source_node_code` varchar(64) NOT NULL COMMENT '模板节点 code（快照来源）',
  `snapshot` json DEFAULT NULL COMMENT '创建时节点快照（title/instruction/options/required/time_rule/input_type）',
  `answer` json DEFAULT NULL COMMENT '本次填写结果（单选code/多选codes/文本）',
  `applicability` varchar(20) NOT NULL DEFAULT 'APPLICABLE' COMMENT 'APPLICABLE/NOT_APPLICABLE',
  `due_date` date DEFAULT NULL COMMENT '本次计划到期日（空=待排期）',
  `priority` varchar(16) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  `progress` varchar(16) NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'UNPROCESSED/PROCESSING/COMPLETED',
  `note` text COMMENT '私人备注（仅本人，≤2000 字）',
  `waiting_reason` varchar(500) DEFAULT NULL COMMENT '等待跟进：在等什么',
  `next_action` varchar(500) DEFAULT NULL COMMENT '等待跟进：下一步行动',
  `review_date` date DEFAULT NULL COMMENT '跟进复查日',
  `remind_enabled` tinyint NOT NULL DEFAULT '1',
  `remind_days` int NOT NULL DEFAULT '3',
  `schedule_revision` int NOT NULL DEFAULT '0' COMMENT '提醒计划版本（改期/取消递增失效旧节点）',
  `completed_at` datetime DEFAULT NULL,
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_exec_node` (`execution_id`,`source_node_code`),
  KEY `idx_task_exec_progress` (`execution_id`,`progress`),
  KEY `idx_task_progress_due` (`progress`,`due_date`),
  KEY `idx_task_review` (`review_date`,`progress`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人执行任务（快照+答案+计划+进度）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_code` varchar(64) NOT NULL COMMENT '跨版本稳定标识（首次生成后不变）',
  `document_id` bigint NOT NULL COMMENT '所属文档 knowledge_document.id',
  `document_version_id` bigint DEFAULT NULL COMMENT '所属文档版本 knowledge_doc_version.id',
  `previous_item_id` bigint DEFAULT NULL COMMENT '上一版本同一事项 id（版本血缘）',
  `change_type` varchar(16) NOT NULL DEFAULT '不变' COMMENT '新增/修改/删除/不变',
  `title` varchar(200) NOT NULL COMMENT '事项标题',
  `content` text COMMENT '要点内容',
  `stage` varchar(32) NOT NULL DEFAULT 'OTHER' COMMENT 'INITIATION/FINANCE/EXECUTION/VERIFICATION/SPECIAL/OTHER',
  `time_note` varchar(200) DEFAULT NULL COMMENT '时间要求（文本）',
  `due_date` date DEFAULT NULL COMMENT '到期日期',
  `priority` varchar(16) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/CONFIRMING/COMPLETED（全局四态，仅维护人可改）',
  `is_draft` tinyint NOT NULL DEFAULT '1' COMMENT '1=待确认草稿 0=已发布',
  `pending_confirm` tinyint NOT NULL DEFAULT '0' COMMENT '跨版本匹配待维护人确认（相似度档）',
  `project_id` bigint DEFAULT NULL COMMENT '预留弱关联 project.id',
  `remark` varchar(500) DEFAULT NULL COMMENT '全局备注',
  `last_due_notified` date DEFAULT NULL COMMENT '全局责任人到期提醒最近推送日期',
  `update_by_name` varchar(50) DEFAULT NULL COMMENT '最后更新人用户名',
  `create_by_name` varchar(50) DEFAULT NULL COMMENT '创建人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `withdrawn` tinyint NOT NULL DEFAULT '0' COMMENT '1=已按文档撤下（历史保留）',
  `withdrawn_by` varchar(64) DEFAULT NULL,
  `withdrawn_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_code` (`item_code`),
  KEY `idx_ci_document` (`document_id`),
  KEY `idx_ci_status_due` (`status`,`due_date`),
  KEY `idx_ci_stage` (`stage`),
  KEY `idx_ci_draft` (`is_draft`,`document_id`),
  KEY `idx_ci_doc_withdrawn` (`document_id`,`withdrawn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规事项主表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_item_assignee` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_user` (`item_id`,`user_id`),
  KEY `idx_assignee_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项责任人关联（多人）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_item_hide` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cih_user_item` (`user_id`,`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规记事本个人隐藏（仅影响当前员工）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_item_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `field_name` varchar(32) NOT NULL COMMENT '变更字段或审计动作（草稿发布/下架/恢复/删除/匹配确认/责任人等）',
  `old_value` varchar(500) DEFAULT NULL,
  `new_value` varchar(500) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `change_reason` varchar(200) DEFAULT NULL COMMENT '变更原因（可选填）',
  PRIMARY KEY (`id`),
  KEY `idx_history_item` (`item_id`,`operation_time`),
  KEY `idx_history_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项修改历史（字段变更+审计动作）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_project_doc_binding` (
  `project_id` bigint NOT NULL,
  `document_id` bigint NOT NULL,
  `doc_title` varchar(255) NOT NULL COMMENT '绑定时文档名快照',
  `bind_by` varchar(64) NOT NULL,
  `bind_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目×来源文档绑定（首次推送写入，本期无换绑）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_project_todo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `source_item_id` bigint NOT NULL,
  `document_id` bigint NOT NULL,
  `document_version_id` bigint DEFAULT NULL,
  `doc_title` varchar(255) DEFAULT NULL COMMENT '推送时文档名快照',
  `title` varchar(255) NOT NULL COMMENT '推送时标题快照',
  `content` text COMMENT '推送时内容快照',
  `source_anchor` varchar(255) DEFAULT NULL COMMENT '来源引用（版本号/章节锚点）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE',
  `handle_by` varchar(64) DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `row_version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cpt_project_item` (`project_id`,`source_item_id`),
  KEY `idx_cpt_project_status` (`project_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目合规待办（来源快照，撤下/更新不影响）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_reminder_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `reminder_type` varchar(32) NOT NULL COMMENT 'due_before / due_today / overdue / assignee_due / done_notify 等',
  `scheduled_time` datetime NOT NULL COMMENT '计划提醒时间（自然日）',
  `sent_time` datetime DEFAULT NULL,
  `send_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/SKIPPED',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数（上限 3）',
  `workday_only` tinyint NOT NULL DEFAULT '0' COMMENT '是否仅工作日（预留，下轮实现）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reminder_unique` (`user_id`,`item_id`,`reminder_type`,`scheduled_time`),
  KEY `idx_reminder_status` (`send_status`,`scheduled_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规提醒记录（唯一约束幂等）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_extraction` (
  `id` varchar(36) NOT NULL,
  `document_id` bigint NOT NULL,
  `source_version` int NOT NULL,
  `result` json NOT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_workspace_extraction_document` (`document_id`,`source_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version_id` bigint NOT NULL COMMENT '模板版本',
  `parent_id` bigint DEFAULT NULL COMMENT '父分组节点（同版本；最多两级）',
  `node_code` varchar(64) NOT NULL COMMENT '节点稳定标识（同版本唯一）',
  `title` varchar(200) NOT NULL,
  `stage` varchar(32) DEFAULT NULL COMMENT '阶段（沿用 v3 枚举）',
  `input_type` varchar(24) NOT NULL DEFAULT 'CHECK' COMMENT 'CHECK 说明/检查项、SINGLE 单选、MULTI 多选、TEXT 文本',
  `instruction` text COMMENT '操作说明/说明文字',
  `materials` text COMMENT '材料清单',
  `acceptance` text COMMENT '完成判定/验收标准',
  `time_rule` varchar(200) DEFAULT NULL COMMENT '规范时限（原文要求，如 会前7天提交）',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必做',
  `source_anchor` varchar(500) DEFAULT NULL COMMENT '原文引用（页码/章节）',
  `condition_option_id` bigint DEFAULT NULL COMMENT '前置选项（简单分支：选了该选项才适用）',
  `sort` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_version_code` (`version_id`,`node_code`),
  KEY `idx_node_version_parent` (`version_id`,`parent_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规规范节点（分组+细项）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `node_id` bigint NOT NULL COMMENT '所属单选/多选节点',
  `option_code` varchar(64) NOT NULL COMMENT '选项稳定标识',
  `label` varchar(200) NOT NULL COMMENT '选项文案',
  `guidance` text COMMENT '该选项适用条件/说明',
  `sort` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_opt_node_code` (`node_id`,`option_code`),
  KEY `idx_opt_node_sort` (`node_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板候选选项';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_review` (
  `document_id` bigint NOT NULL,
  `node_code` varchar(64) NOT NULL,
  `content_hash` char(64) NOT NULL,
  `reviewed_by` bigint NOT NULL,
  `reviewed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`document_id`,`node_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL COMMENT '来源知识文档',
  `source_document_version_id` bigint DEFAULT NULL COMMENT '来源文档版本（快照依据）',
  `revision` int NOT NULL DEFAULT '1' COMMENT '同文档模板修订号',
  `state` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
  `owner_user_id` bigint NOT NULL COMMENT '草稿所有者（上传者）',
  `published_by` bigint DEFAULT NULL COMMENT '发布人',
  `title` varchar(200) NOT NULL COMMENT '模板名称',
  `applicability` text COMMENT '适用说明',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `published_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tv_doc_revision` (`document_id`,`revision`),
  KEY `idx_tv_doc_state` (`document_id`,`state`,`revision`),
  KEY `idx_tv_owner` (`owner_user_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规规范模板版本';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_workspace` (
  `document_id` bigint NOT NULL,
  `content` json NOT NULL,
  `row_version` int NOT NULL DEFAULT '0',
  `selection_resolved` tinyint NOT NULL DEFAULT '0',
  `active_template_version_id` bigint DEFAULT NULL,
  `active_content_hash` char(64) DEFAULT NULL,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_template_workspace_request` (
  `document_id` bigint NOT NULL,
  `request_id` varchar(64) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `actor_user_id` bigint DEFAULT NULL,
  `result` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`document_id`,`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_user_mode` (
  `user_id` bigint NOT NULL,
  `mode` varchar(8) NOT NULL DEFAULT 'V3',
  `legacy_snapshot` json DEFAULT NULL COMMENT '切换时只读旧进度快照，不自动关联项目',
  `switched_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规个人切换开关；缺行沿用V3';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `compliance_user_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `progress_status` varchar(16) NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'UNPROCESSED/PROCESSING/COMPLETED（个人三态，仅本人）',
  `block_reason` varchar(200) DEFAULT NULL COMMENT '阻塞原因（预设或自定义）',
  `remark` varchar(500) DEFAULT NULL COMMENT '个人备注（仅本人）',
  `remind_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '个人提醒开关',
  `remind_days` int NOT NULL DEFAULT '3' COMMENT '提醒提前天数（1-30）',
  `last_due_notified` date DEFAULT NULL COMMENT '个人提醒最近推送日期',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`,`item_id`),
  KEY `idx_up_user_status` (`user_id`,`progress_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人事项进度/提醒/备注（仅本人可见）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '客户姓名',
  `work_unit` varchar(255) DEFAULT NULL COMMENT '工作单位',
  `work_position` varchar(255) DEFAULT NULL COMMENT '工作职位',
  `work_level` varchar(100) DEFAULT NULL COMMENT '工作职级',
  `current_duty` varchar(255) DEFAULT NULL COMMENT '现任职务',
  `preferences` text COMMENT '客户喜好（文本）',
  `remark` text COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0正常 1已删除（默认隐藏，可恢复）',
  `delete_by` varchar(50) DEFAULT NULL COMMENT '删除人',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '最近更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户档案表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `customer_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL COMMENT '客户档案ID customer.id',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `storage_name` varchar(500) NOT NULL COMMENT '存储文件名（客户附件库内）',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件扩展名（小写）',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `upload_by` varchar(50) DEFAULT NULL COMMENT '上传人',
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户档案附件表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `customer_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_customer_id` bigint NOT NULL COMMENT '关系起点客户ID customer.id',
  `to_customer_id` bigint NOT NULL COMMENT '关系终点客户ID customer.id',
  `relation_type_id` bigint NOT NULL COMMENT '关系类型ID customer_relation_type.id',
  `description` varchar(500) DEFAULT NULL COMMENT '关系说明',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_from_to_type` (`from_customer_id`,`to_customer_id`,`relation_type_id`),
  KEY `idx_to_customer_id` (`to_customer_id`),
  KEY `idx_from_customer_id` (`from_customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户关系表（有向，如"介绍关系"）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `customer_relation_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '类型编码（预置固定，自定义为 custom_<时间戳>）',
  `name` varchar(100) NOT NULL COMMENT '类型名称',
  `builtin` tinyint NOT NULL DEFAULT '0' COMMENT '1预置类型 0自定义类型',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用（可停用不可物理删除）',
  `direction` varchar(20) NOT NULL DEFAULT 'DIRECTED' COMMENT '关系方向：DIRECTED单向 / BIDIRECTED双向 / UNDIRECTED无向',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户关系类型表（预置枚举+维护人可扩展）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `download_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '下载人 sys_user.id（可空）',
  `username` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下载人姓名',
  `expert_id` bigint DEFAULT NULL COMMENT '专家 id（全量下载时为 NULL）',
  `expert_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '专家姓名',
  `doc_id` bigint DEFAULT NULL COMMENT '知识文档ID（download_type=knowledge_doc 时有值）',
  `download_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'selected_csv / selected_zip / full_csv / full_zip / md',
  `download_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间',
  `ip` varchar(45) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源 IP',
  PRIMARY KEY (`id`),
  KEY `idx_expert_id` (`expert_id`),
  KEY `idx_download_time` (`download_time`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='下载审计表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `download_log_customer_backup_20261029` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '下载人 sys_user.id（可空）',
  `username` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下载人姓名',
  `expert_id` bigint DEFAULT NULL COMMENT '专家 id（全量下载时为 NULL）',
  `expert_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '专家姓名',
  `doc_id` bigint DEFAULT NULL COMMENT '知识文档ID（download_type=knowledge_doc 时有值）',
  `download_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'selected_csv / selected_zip / full_csv / full_zip / md',
  `download_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间',
  `ip` varchar(45) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源 IP',
  PRIMARY KEY (`id`),
  KEY `idx_expert_id` (`expert_id`),
  KEY `idx_download_time` (`download_time`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='下载审计表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `expert_change_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expert_id` bigint DEFAULT NULL COMMENT '专家ID（新增申请提交时为空，审批通过后回填）',
  `expert_name` varchar(100) NOT NULL COMMENT '专家姓名快照',
  `applicant` varchar(100) NOT NULL COMMENT '申请人用户名',
  `change_data` text NOT NULL COMMENT '变更内容JSON（字段中文键->新值）',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请原因',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审批/1已通过/2已驳回',
  `request_type` tinyint NOT NULL DEFAULT '0' COMMENT '申请类型：0=修改申请，1=新增专家申请',
  `template_id` bigint DEFAULT NULL COMMENT '来源模板 approval_template.id（可空）',
  `approve_by` varchar(100) DEFAULT NULL COMMENT '审批人用户名',
  `current_assignee` varchar(64) DEFAULT NULL COMMENT '当前审批人（转办后有值；空=未转办，任何有审批权限的用户可审）',
  `extra_approver` varchar(64) DEFAULT NULL COMMENT '加签审批人（或签语义，任一审批即终态）',
  `approve_comment` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_expert_id` (`expert_id`),
  KEY `idx_applicant` (`applicant`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专家信息修改审批单';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `expert_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '姓名',
  `unit` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '单位',
  `department` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '科室',
  `title` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职称',
  `research_direction` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '研究方向',
  `expertise_area` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '擅长领域',
  `contact` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系方式',
  `profile` text COLLATE utf8mb4_general_ci COMMENT '简介',
  `id_card` varchar(18) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证号（查看脱敏，前3后4）',
  `bank_card` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '银行卡号（查看脱敏，前3后4）',
  `photo` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证件照文件名（相对照片库目录）',
  `bank_branch` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '银行卡开户行（R10 新增）',
  `id_card_front` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证正面图片文件名（R10 新增，仅管理页维护、下载包含）',
  `id_card_back` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证反面图片文件名（R10 新增，仅管理页维护、下载包含）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `update_by` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人id（登录用户名）',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_department` (`department`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='专家信息表（正式版 18 字段）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `knowledge_doc_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT 'doc id',
  `version` int DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_path` varchar(500) DEFAULT NULL,
  `file_type` varchar(50) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `upload_by` varchar(50) DEFAULT NULL,
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge doc version table';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `knowledge_doc_version_customer_backup_20261029` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT 'doc id',
  `version` int DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_path` varchar(500) DEFAULT NULL,
  `file_type` varchar(50) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `upload_by` varchar(50) DEFAULT NULL,
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='knowledge doc version table';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(50) NOT NULL COMMENT '分类（meeting-execution/association-compliance/enterprise-compliance/customer-preferences）',
  `title` varchar(200) NOT NULL COMMENT '文档标题',
  `version` int NOT NULL DEFAULT '1' COMMENT '当前版本号（从1递增）',
  `file_name` varchar(255) DEFAULT NULL COMMENT '当前文件原始文件名',
  `file_path` varchar(500) DEFAULT NULL COMMENT '当前文件存储文件名（知识文档库内）',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件扩展名（小写）',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `content` mediumtext COMMENT '文档正文（上传时抽取，pdf/doc/docx，截断存储，用于内容检索）',
  `upload_by` varchar(50) DEFAULT NULL COMMENT '首次上传人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '最近更新人',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1上架 0下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `parse_status` varchar(16) DEFAULT NULL COMMENT '解析状态：PENDING/RUNNING/SUCCESS/FAILED；NULL=历史数据/不适用',
  `parse_message` varchar(500) DEFAULT NULL COMMENT '解析结果说明（成功摘要或失败原因）',
  `parse_time` datetime DEFAULT NULL COMMENT '最近一次解析完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_title` (`title`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档主表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `knowledge_document_customer_backup_20261029` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(50) NOT NULL COMMENT '分类（meeting-execution/association-compliance/enterprise-compliance/customer-preferences）',
  `title` varchar(200) NOT NULL COMMENT '文档标题',
  `version` int NOT NULL DEFAULT '1' COMMENT '当前版本号（从1递增）',
  `file_name` varchar(255) DEFAULT NULL COMMENT '当前文件原始文件名',
  `file_path` varchar(500) DEFAULT NULL COMMENT '当前文件存储文件名（知识文档库内）',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件扩展名（小写）',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `content` mediumtext COMMENT '文档正文（上传时抽取，pdf/doc/docx，截断存储，用于内容检索）',
  `upload_by` varchar(50) DEFAULT NULL COMMENT '首次上传人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '最近更新人',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1上架 0下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_title` (`title`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档主表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `knowledge_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT '文档ID knowledge_document.id',
  `user_id` bigint NOT NULL COMMENT '用户ID sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_user` (`doc_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档收藏表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `knowledge_favorite_customer_backup_20261029` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT '文档ID knowledge_document.id',
  `user_id` bigint NOT NULL COMMENT '用户ID sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_user` (`doc_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档收藏表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '接收人 sys_user.id',
  `type` varchar(50) NOT NULL COMMENT '事件类型（approval_result/approval_todo/role_changed/password_reset/doc_off_shelf/doc_deleted）',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` varchar(500) DEFAULT NULL COMMENT '通知内容',
  `jump_path` varchar(255) DEFAULT NULL COMMENT '点击跳转前端路由（如 /profile/my-approvals?status=1）',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0 未读 / 1 已读',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_read` (`user_id`,`is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用消息通知表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '操作人 sys_user.id（可空，如历史迁移缺失）',
  `username` varchar(64) DEFAULT NULL COMMENT '操作人用户名',
  `action_type` varchar(32) NOT NULL COMMENT '操作类型枚举：DOWNLOAD/LOGIN/LOGOUT/CHANGE_PASSWORD/RESET_PASSWORD/CREATE_USER/UPDATE_USER/DELETE_USER/ASSIGN_ROLES/ROLE_PERMISSION/CREATE_EXPERT/UPDATE_EXPERT/DELETE_EXPERT/IMPORT_EXPERT/CREATE_DOC/UPDATE_DOC/OFF_SHELF_DOC/DELETE_DOC',
  `action_desc` varchar(500) DEFAULT NULL COMMENT '操作描述（含操作对象中文名）',
  `target_type` varchar(32) DEFAULT NULL COMMENT '对象类型：USER/ROLE/EXPERT/DOC 等',
  `target_id` varchar(64) DEFAULT NULL COMMENT '对象标识（id 或用户名）',
  `ip` varchar(64) DEFAULT NULL COMMENT '来源 IP',
  `source_id` bigint DEFAULT NULL COMMENT '来源记录ID（download_log.id，历史迁移标记；新写入为 NULL）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source` (`action_type`,`source_id`),
  KEY `idx_username` (`username`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户敏感操作日志表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '权限码（可重复：菜单/接口可共用同一码）',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `type` tinyint NOT NULL COMMENT '1=菜单 2=接口',
  `method` varchar(10) DEFAULT NULL COMMENT '接口 HTTP 方法；菜单行为 MENU',
  `path` varchar(200) DEFAULT NULL COMMENT '接口路径（Ant 模式）；菜单行为权限码',
  `parent_code` varchar(50) DEFAULT NULL COMMENT '父菜单权限码（预留）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_path` (`method`,`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限点表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '项目名称（必填）',
  `category` varchar(20) NOT NULL COMMENT '大类：ONLINE 线上 / OFFLINE 线下（固定）',
  `type_id` bigint DEFAULT NULL COMMENT '类型 project_type.id',
  `start_date` date DEFAULT NULL COMMENT '计划开始时间',
  `end_date` date DEFAULT NULL COMMENT '计划结束时间',
  `owner_id` bigint DEFAULT NULL COMMENT '负责人 sys_user.id（限定拥有 PROJECT_OWNER 角色用户；立项必填）',
  `creator_id` bigint NOT NULL COMMENT '创建人 sys_user.id',
  `phase_id` bigint NOT NULL COMMENT '当前阶段 project_phase.id（项目状态）',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 正常 / CANCELED 已取消 / DELETED 已软删',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因（取消必填）',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `budget_amount` decimal(14,2) DEFAULT NULL COMMENT '预算金额（BUDGET 模板登记，项目级全局一份）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `team` varchar(20) DEFAULT NULL COMMENT '团队：DEPT1 项目一部 / DEPT2 项目二部 / DEPT3 项目三部',
  `city_id` bigint DEFAULT NULL COMMENT '会议城市 supplier_city.id',
  `meeting_start_date` date DEFAULT NULL COMMENT '会议举办开始日期（单日=与结束同日）',
  `meeting_end_date` date DEFAULT NULL COMMENT '会议举办结束日期',
  `client_company` varchar(200) DEFAULT NULL COMMENT '客户单位',
  `client_name` varchar(100) DEFAULT NULL COMMENT '客户姓名',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_project_phase` (`phase_id`),
  KEY `idx_project_owner` (`owner_id`),
  KEY `idx_project_type` (`type_id`),
  KEY `idx_project_category` (`category`),
  KEY `idx_project_status` (`status`),
  KEY `idx_project_team` (`team`),
  KEY `idx_project_city` (`city_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目主表（软删：status=DELETED 列表隐藏，数据保留）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_assistant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `user_id` bigint NOT NULL COMMENT '协助人 sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pa_project_user` (`project_id`,`user_id`),
  KEY `idx_pa_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目协助人（多选，全员可选）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_config_request` (
  `request_id` varchar(64) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `result` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_config_snapshot` (
  `project_id` bigint NOT NULL,
  `source_revision_id` bigint DEFAULT NULL,
  `schema_version` int NOT NULL,
  `effective_config` json NOT NULL,
  `config_hash` char(64) NOT NULL,
  `origin` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_config_state` (
  `id` bigint NOT NULL,
  `epoch` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `expert_id` bigint NOT NULL COMMENT '专家 expert_info.id',
  `expert_name` varchar(50) NOT NULL COMMENT '专家姓名快照',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号快照（自动带出专家库，可改并回写 expert_info）',
  `bank_card` varchar(30) DEFAULT NULL COMMENT '银行卡号快照（自动带出专家库，可改并回写 expert_info）',
  `fee` decimal(14,2) DEFAULT NULL COMMENT '合同金额',
  `file_path` varchar(500) DEFAULT NULL COMMENT '合同文件路径（上传后登记）',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PENDING 审批中 / APPROVED 已生效 / REJECTED 已驳回',
  `approval_ref` bigint DEFAULT NULL COMMENT '审批单 id（复用 approval 模块 application.id）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '登记人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contract_project` (`project_id`),
  KEY `idx_contract_expert` (`expert_id`),
  KEY `idx_contract_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专家合同（登记信息+上传文件+发起审批；敏感字段回写 expert_info 并留痕）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_doc` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `phase_id` bigint NOT NULL COMMENT '挂载阶段 project_phase.id（分阶段挂载）',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(500) NOT NULL COMMENT '文件路径（本地存储，同 expert 照片模式）',
  `upload_by` varchar(64) DEFAULT NULL COMMENT '上传人用户名',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记：1已删除',
  `deleted_by` varchar(64) DEFAULT NULL COMMENT '删除人用户名（留痕）',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间（留痕）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_project_phase` (`project_id`,`phase_id`),
  KEY `idx_doc_uploader` (`upload_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分阶段资料（上传无类型/大小限制；删除软删留痕）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_executor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `user_id` bigint NOT NULL COMMENT '执行人 sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user` (`project_id`,`user_id`),
  KEY `idx_executor_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目执行人（多人；创建时默认带入创建人）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_expert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `expert_id` bigint NOT NULL COMMENT '专家 expert_info.id',
  `role` varchar(100) DEFAULT NULL COMMENT '角色/分工（主持人/演讲嘉宾/评审专家等）',
  `fee` decimal(14,2) DEFAULT NULL COMMENT '劳务费（结算阶段登记）',
  `contract_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '合同状态快照：NONE/DRAFT/PENDING/APPROVED/REJECTED',
  `create_by` varchar(64) DEFAULT NULL COMMENT '关联人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_expert` (`project_id`,`expert_id`),
  KEY `idx_expert_rel_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目-专家关联（全局一份，不分阶段；移出留痕 operation_log）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_phase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '阶段名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用（停用不再可选）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phase_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目流程阶段配置（可配置，项目当前阶段=状态）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_phase_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目ID（project.id）',
  `from_phase_id` bigint NOT NULL COMMENT '原阶段（project_phase.id）',
  `to_phase_id` bigint NOT NULL COMMENT '目标阶段（project_phase.id）',
  `action_type` varchar(16) NOT NULL COMMENT '动作：ADVANCE 推进 / ROLLBACK 回退（含跨级）',
  `reason` varchar(500) DEFAULT NULL COMMENT '原因（回退必填，推进为空）',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_phase_hist_project` (`project_id`,`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目阶段变更历史（推进/回退含跨级）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_phase_template_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phase_id` bigint NOT NULL COMMENT '阶段 project_phase.id',
  `template_id` bigint NOT NULL COMMENT '模板 project_template.id',
  `type_id` bigint NOT NULL DEFAULT '0' COMMENT '项目类型ID，0=全局兜底绑定',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填：1必填 0选填（推进校验用）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_phase_template` (`type_id`,`phase_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶段-模板绑定（管理员可配置，required 必填标记）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 id',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名（仅展示，不参与存储路径）',
  `file_path` varchar(255) NOT NULL COMMENT '项目资料库随机文件名',
  `file_size` bigint DEFAULT NULL COMMENT '字节',
  `mime_type` varchar(100) DEFAULT NULL,
  `parse_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/DONE/FAILED',
  `parse_message` varchar(500) DEFAULT NULL,
  `raw_result` json DEFAULT NULL COMMENT '原始解析输出（回溯用）',
  `is_current` tinyint NOT NULL DEFAULT '1' COMMENT '1=当前生效份，历史份=0',
  `uploaded_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ps_project` (`project_id`,`is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目日程文件';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_speaker` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `schedule_id` bigint DEFAULT NULL COMMENT '来源日程；手动补录为 NULL',
  `expert_id` bigint DEFAULT NULL COMMENT '匹配到的专家档案 id；NULL=库外专家',
  `expert_name` varchar(100) NOT NULL,
  `role` varchar(20) NOT NULL COMMENT 'CHAIR 大会主席/HOST 大会主持/SPEAKER 大会讲者/DISCUSSANT 讨论嘉宾',
  `segment_title` varchar(255) DEFAULT NULL COMMENT '议题/段落',
  `segment_time` varchar(100) DEFAULT NULL COMMENT '时间段文本',
  `confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '0=解析候选，1=人工确认正式记录',
  `created_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_spk_project` (`project_id`,`confirmed`),
  KEY `idx_spk_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目参会专家（日程解析结果+人工补录）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `supplier_id` bigint NOT NULL COMMENT '供应商 supplier.id',
  `purpose` varchar(100) DEFAULT NULL COMMENT '用途（按供应商类型自动带出，不手填）',
  `fee` decimal(14,2) DEFAULT NULL COMMENT '费用（过程中可填写）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '关联人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_supplier` (`project_id`,`supplier_id`),
  KEY `idx_supplier_rel_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目-供应商关联（全局一份；用途按类型自动带出；移出留痕）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '模板名称',
  `template_type` varchar(20) NOT NULL COMMENT '模板类型枚举：FILE/EXPERT/SUPPLIER/BUDGET/RICH_TEXT',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人用户名（内置为 SYSTEM）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置项模板（内置 5 类 + 管理员可新建模板类型）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '类型名称（管理员可增删改）',
  `sort` int NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_config_revision_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`name`),
  UNIQUE KEY `uk_project_type_position` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目类型字典';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_type_config_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `schema_version` int NOT NULL,
  `definition` json NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_revision` (`type_id`,`revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `project_type_phase_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type_id` bigint NOT NULL COMMENT '项目类型ID（project_type.id）',
  `phase_id` bigint NOT NULL COMMENT '流程阶段ID（project_phase.id）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '阶段顺序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_phase` (`type_id`,`phase_id`),
  KEY `idx_type_id` (`type_id`),
  KEY `idx_phase_id` (`phase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目类型-流程阶段关联（类型自定义阶段序列）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `builtin` tinyint NOT NULL DEFAULT '0' COMMENT '1=内置角色（禁删、禁改code；SUPER_ADMIN 锁定不可改）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID(role.id)',
  `permission_code` varchar(50) NOT NULL COMMENT '权限码（关联 permission.code）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `schema_migration` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `script` varchar(191) NOT NULL COMMENT '迁移文件名（database/migration 下，含日期前缀）',
  `checksum` char(64) NOT NULL COMMENT '脚本文件 SHA256（漂移检测）',
  `status` varchar(16) NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED/BASELINE/FAILED',
  `applied_at` datetime DEFAULT NULL COMMENT '执行时间',
  `applied_by` varchar(64) DEFAULT NULL COMMENT '执行人（本机账号）',
  `note` varchar(500) DEFAULT NULL COMMENT '备注（回填来源/说明）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script` (`script`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据库迁移版本表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '供应商名称',
  `type_id` bigint NOT NULL COMMENT '类型 supplier_type.id',
  `city_id` bigint NOT NULL COMMENT '城市 supplier_city.id（一供应商一城市）',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `contact` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(50) DEFAULT NULL COMMENT '电话',
  `wechat` varchar(50) DEFAULT NULL COMMENT '微信',
  `description` text COMMENT '描述',
  `images` text COMMENT '图片文件名 JSON 数组（前端压缩后上传，存储于供应商图库）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_supplier_type` (`type_id`),
  KEY `idx_supplier_city` (`city_id`),
  KEY `idx_supplier_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商档案（全员创建维护，无审批）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier_city` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province` varchar(50) NOT NULL COMMENT '省/直辖市/自治区/特别行政区',
  `name` varchar(50) NOT NULL COMMENT '城市名',
  `code` varchar(6) DEFAULT NULL COMMENT '民政部行政区划码（省+地级两级）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_province_city` (`province`,`name`),
  UNIQUE KEY `uk_city_code` (`code`),
  KEY `idx_city_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商城市字典（全国预置 + 录入自动沉淀）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier_hotel_print_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hotel_id` bigint NOT NULL COMMENT '酒店 supplier.id（类型 biz_key=HOTEL）',
  `print_shop_id` bigint NOT NULL COMMENT '打印店 supplier.id（类型 biz_key=PRINT）',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hotel_print` (`hotel_id`,`print_shop_id`),
  KEY `idx_rel_print` (`print_shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='酒店-打印店多对多绑定';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `supplier_id` bigint NOT NULL COMMENT '供应商ID supplier.id',
  `file_name` varchar(100) NOT NULL COMMENT '磁盘文件名（supplier_<uuid>.<ext>）',
  `name` varchar(50) NOT NULL DEFAULT '' COMMENT '自定义显示名（≤50字符，空=显示原文件名）',
  `is_main` tinyint NOT NULL DEFAULT '0' COMMENT '是否主图 1=是 0=否（每供应商至多一张主图）',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序（升序）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `main_key` bigint GENERATED ALWAYS AS (if((`is_main` = 1),`supplier_id`,NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_file` (`supplier_id`,`file_name`),
  UNIQUE KEY `uk_supplier_main` (`supplier_id`,`main_key`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_is_main` (`supplier_id`,`is_main`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商图片表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier_price_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `supplier_id` bigint NOT NULL,
  `item_name` varchar(100) NOT NULL COMMENT '服务项',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '价格',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `sort` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `quote_date` date DEFAULT NULL COMMENT '报价日期（NULL=未填写，回退用创建时间）',
  `valid_until` date DEFAULT NULL COMMENT '报价有效期至（NULL=长期有效）',
  PRIMARY KEY (`id`),
  KEY `idx_price_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商行价（服务项+价格+单位+备注）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `supplier_id` bigint NOT NULL,
  `username` varchar(64) NOT NULL COMMENT '评价人姓名（即时显示）',
  `stars` tinyint NOT NULL COMMENT '1-5 星',
  `content` varchar(500) DEFAULT NULL COMMENT '评价内容',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_review_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商评价（1-5星+文字，即发即显）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supplier_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_key` varchar(20) DEFAULT NULL COMMENT '固定业务键：HOTEL/PRINT/MEDIA/IMAGING/FLEET/TICKET（内置类型锁定不改；自定义类型 NULL）',
  `name` varchar(50) NOT NULL COMMENT '类型名称（管理员可改）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`name`),
  UNIQUE KEY `uk_type_biz_key` (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商类型字典';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录账号 = 员工姓名',
  `password` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'BCrypt 加密密码（禁止明文）',
  `is_admin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0 普通员工 / 1 管理员（管理员名单）',
  `wecom_userid` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '二期企微 userid（预留）',
  `mobile` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号（唯一登录标识，未绑定用户为 NULL）',
  `position` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职务（企微 user/get position，同步展示用；非登录标识）',
  `must_change_password` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否需强制改密：1是 0否（设计文档 5.1 首次登录强制改密）',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1 启用 / 0 停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `token_version` int NOT NULL DEFAULT '0' COMMENT 'Token 版本号：停用/删除/改密/改角色时自增，旧 Token 立即失效',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wecom_userid` (`wecom_userid`),
  UNIQUE KEY `uk_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `user_dashboard_layout` (
  `user_id` bigint NOT NULL,
  `layout` json NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `layout_v2` json DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `user_quick_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `item_key` varchar(64) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`,`item_key`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宸ヤ綔鍙板揩鎹疯?闂?釜鎬у寲閰嶇疆';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID(sys_user.id)',
  `role_id` bigint NOT NULL COMMENT '角色ID(role.id)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `wecom_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dept_id` bigint NOT NULL COMMENT '企微部门id（根=1）',
  `name` varchar(100) NOT NULL COMMENT '部门名称',
  `parent_dept_id` bigint DEFAULT NULL COMMENT '父部门id（根部门为 NULL）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '部门排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0企微已删除（保留快照）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企微部门快照表（只存快照，不回写企微）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `wecom_migration_uk_username_backup` (
  `backup_id` bigint NOT NULL AUTO_INCREMENT,
  `idx_name` varchar(64) NOT NULL COMMENT '索引名',
  `column_name` varchar(64) NOT NULL COMMENT '索引列',
  `seq_in_index` int NOT NULL COMMENT '列在索引中的序号',
  `non_unique` int NOT NULL COMMENT '0唯一 1非唯一',
  `index_type` varchar(32) NOT NULL COMMENT '索引类型',
  `backup_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
  PRIMARY KEY (`backup_id`),
  UNIQUE KEY `uk_backup_idx` (`idx_name`,`seq_in_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='uk_username 删除前备份表（回滚重建时参考）';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `wecom_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trigger_type` varchar(20) NOT NULL COMMENT 'MANUAL/SCHEDULED/DRYRUN',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作人（定时任务为 system）',
  `status` varchar(20) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
  `fetched_users` int NOT NULL DEFAULT '0' COMMENT '拉取成员数',
  `created_users` int NOT NULL DEFAULT '0' COMMENT '新建用户数',
  `updated_users` int NOT NULL DEFAULT '0' COMMENT '更新已绑定用户数',
  `disabled_users` int NOT NULL DEFAULT '0' COMMENT '停用用户数',
  `bound_users` int NOT NULL DEFAULT '0' COMMENT '自动绑定存量用户数',
  `unbound_left` int NOT NULL DEFAULT '0' COMMENT '剩余未绑定存量用户数',
  `error_msg` text COMMENT '失败原因',
  `detail` text COMMENT 'DryRun 变更清单 JSON（前端试算展示）',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企微通讯录同步审计日志';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `wecom_user_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'sys_user.id',
  `dept_id` bigint NOT NULL COMMENT '企微部门id',
  `is_main` tinyint NOT NULL DEFAULT '0' COMMENT '1主部门 0非主部门（企微 department 数组第一项为主部门）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_dept` (`user_id`,`dept_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企微成员-部门归属表';

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ------------------------------------------------------------
-- 二、字典与权限种子数据
-- ------------------------------------------------------------

-- 初始管理员（登录账号 admin，密码 111111；部署后请立即修改）
INSERT IGNORE INTO `sys_user` (`username`, `password`, `is_admin`, `must_change_password`, `status`) VALUES
  ('admin', '$2a$10$FECPAy5iIj1rMBwiGKRc0uxg5b8kIlO4lSnHLowNxtdMrJyAo0brK', 1, 0, 1);
-- MySQL dump 10.13  Distrib 26.7.0, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: expert_system
-- ------------------------------------------------------
-- Server version	26.7.0

/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `role`
--

LOCK TABLES `role` WRITE;
/*!40000 ALTER TABLE `role` DISABLE KEYS */;
INSERT IGNORE INTO `role` (`id`, `code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES (1,'SUPER_ADMIN','超级管理员','系统最高权限，代码内强制全权，权限绑定锁定不可修改',1,1,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `role` (`id`, `code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES (2,'ADMIN','管理员','专家管理、审批、用户管理、下载留痕等全部业务权限',1,1,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `role` (`id`, `code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES (3,'APPROVER','审批人','审批流处理（approval:audit）',1,1,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `role` (`id`, `code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES (4,'EMPLOYEE','普通员工','默认登录角色，无业务权限（可提交审批申请）',1,1,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `role` (`id`, `code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES (52,'KNOWLEDGE_MAINTAINER','知识星球维护人','知识星球文档上传/更新/下架/上架/永久删除/版本历史/下载次数维护',1,1,'2026-10-27 14:51:58','2026-10-27 14:51:58');
INSERT IGNORE INTO `role` (`id`, `code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES (53,'PROJECT_OWNER','项目负责人','项目负责人：负责项目创建/推进/关联/结算等（绑 project:manage）',1,1,'2026-10-28 15:52:04','2026-10-28 15:52:04');
/*!40000 ALTER TABLE `role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `permission`
--

LOCK TABLES `permission` WRITE;
/*!40000 ALTER TABLE `permission` DISABLE KEYS */;
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (1,'expert:manage','专家管理',1,'MENU','expert:manage',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (2,'audit:manage','下载留痕',1,'MENU','audit:manage',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (3,'approval:manage','审批管理',1,'MENU','approval:manage',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (4,'approval:config','审批流配置',1,'MENU','approval:config',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (5,'user:manage','用户管理',1,'MENU','user:manage',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (6,'role:manage','角色管理',1,'MENU','role:manage',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (7,'expert:add','专家新增',2,'POST','/api/experts',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (8,'expert:edit','专家修改',2,'PUT','/api/experts/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (9,'expert:delete','专家删除',2,'DELETE','/api/experts/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (10,'expert:import','专家导入',2,'POST','/api/experts/import',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (11,'expert:template','导入模板',2,'GET','/api/experts/template',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (12,'expert:export','专家导出-全量CSV',2,'GET','/api/experts/export',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (13,'expert:export','专家导出-员工ZIP',2,'GET','/api/experts/export/employee',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (14,'expert:export','专家导出-兼容全量ZIP',2,'GET','/api/export/full',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (15,'expert:export','专家导出-全量ZIP',2,'GET','/api/experts/export/full',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (16,'approval:audit','审批处理-通过',2,'POST','/api/approval/requests/*/approve',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (17,'approval:audit','审批处理-驳回',2,'POST','/api/approval/requests/*/reject',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (18,'approval:audit','审批列表',2,'GET','/api/approval/requests',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (19,'approval:config','审批流配置-接口',2,'PUT','/api/approval/config',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (20,'audit:view','下载留痕查看',2,'GET','/api/audit/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (21,'user:manage','用户管理-列表',2,'GET','/api/users/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (22,'user:manage','用户管理-新增',2,'POST','/api/users/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (23,'user:manage','用户管理-修改/分配角色',2,'PUT','/api/users/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (24,'user:manage','用户管理-删除',2,'DELETE','/api/users/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (25,'upload:manage','照片上传',2,'POST','/api/upload/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (26,'upload:manage','照片资源',2,'GET','/api/upload/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (27,'role:manage','角色管理-列表/详情',2,'GET','/api/roles/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (28,'role:manage','角色管理-新增',2,'POST','/api/roles/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (29,'role:manage','角色管理-修改/绑定权限',2,'PUT','/api/roles/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (30,'role:manage','角色管理-删除',2,'DELETE','/api/roles/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (31,'role:manage','权限点列表',2,'GET','/api/permissions/**',NULL,'2026-10-22 14:58:22','2026-10-22 14:58:22');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (56,'approval:audit','审批转办',2,'POST','/api/approval/requests/*/transfer',NULL,'2026-10-26 16:14:30','2026-10-26 16:18:00');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (57,'approval:audit','审批加签',2,'POST','/api/approval/requests/*/add-approver',NULL,'2026-10-26 16:14:30','2026-10-26 16:18:00');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (58,'approval:audit','可转办/加签用户列表',2,'GET','/api/approval/assignable-users',NULL,'2026-10-26 16:14:30','2026-10-26 16:18:00');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (59,'supplier:dict','供应商类型-新增',2,'POST','/api/supplier/types',NULL,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (60,'supplier:dict','供应商类型-修改',2,'PUT','/api/supplier/types/**',NULL,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (61,'supplier:dict','供应商类型-删除',2,'DELETE','/api/supplier/types/**',NULL,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (69,'knowledge:maintain','知识文档上传',2,'POST','/api/knowledge/docs',NULL,'2026-10-27 14:51:58','2026-10-27 14:51:58');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (70,'knowledge:maintain','知识文档更新',2,'PUT','/api/knowledge/docs/**',NULL,'2026-10-27 14:51:58','2026-10-27 14:51:58');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (71,'knowledge:maintain','知识文档上架',2,'POST','/api/knowledge/docs/*/relist',NULL,'2026-10-27 14:51:58','2026-10-27 14:51:58');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (72,'knowledge:maintain','知识文档版本历史',2,'GET','/api/knowledge/docs/*/versions',NULL,'2026-10-27 14:51:58','2026-10-27 14:51:58');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (74,'knowledge:maintain','知识文档下架与永久删除',2,'DELETE','/api/knowledge/docs/**',NULL,'2026-10-27 14:53:07','2026-10-27 14:53:07');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (75,'wecom:sync','企微同步-触发同步',2,'POST','/api/admin/wecom/sync',NULL,'2026-10-27 20:23:24','2026-10-27 20:23:24');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (76,'wecom:sync','企微同步-同步状态',2,'GET','/api/admin/wecom/sync/status',NULL,'2026-10-27 20:23:24','2026-10-27 20:23:24');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (77,'wecom:sync','企微同步-最近记录',2,'GET','/api/admin/wecom/sync/recent',NULL,'2026-10-27 20:23:24','2026-10-27 20:23:24');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (78,'wecom:sync','企微同步-部门树',2,'GET','/api/admin/wecom/departments',NULL,'2026-10-27 20:23:24','2026-10-27 20:23:24');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (79,'wecom:sync','企微同步-未绑定清单',2,'GET','/api/admin/wecom/unbound',NULL,'2026-10-27 20:23:24','2026-10-27 20:23:24');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (80,'wecom:sync','企微同步-人工指认绑定',2,'POST','/api/admin/users/*/bind-wecom',NULL,'2026-10-27 20:23:24','2026-10-27 20:23:24');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (81,'project:create','项目创建（全员）',2,'POST','/api/project',NULL,'2026-10-28 15:52:04','2026-10-28 17:52:33');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (82,'project:manage','项目编辑',2,'PUT','/api/project/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (83,'project:manage','项目软删',2,'DELETE','/api/project/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (84,'project:manage','专家关联',2,'POST','/api/project/*/experts',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (85,'project:manage','新建专家卡',2,'POST','/api/project/*/experts/new-card',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (86,'project:manage','专家移出',2,'DELETE','/api/project/*/experts/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (87,'project:manage','登记专家劳务费',2,'PUT','/api/project/*/experts/*/fee',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (88,'project:manage','供应商关联',2,'POST','/api/project/*/suppliers',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (89,'project:manage','供应商移出',2,'DELETE','/api/project/*/suppliers/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (94,'project:manage','合同更新',2,'PUT','/api/project/*/contracts/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (96,'project:manage','合同删除',2,'DELETE','/api/project/*/contracts/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (98,'project:manage','登记供应商费用',2,'PUT','/api/project/*/suppliers/*/fee',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (100,'project:manage','类型字典新增',2,'POST','/api/project/config/types',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (101,'project:manage','类型字典修改',2,'PUT','/api/project/config/types/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (102,'project:manage','类型字典删除',2,'DELETE','/api/project/config/types/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (103,'project:manage','阶段新增',2,'POST','/api/project/config/phases',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (104,'project:manage','阶段修改',2,'PUT','/api/project/config/phases/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (105,'project:manage','阶段删除',2,'DELETE','/api/project/config/phases/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (106,'project:manage','模板新增',2,'POST','/api/project/config/templates',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (107,'project:manage','模板修改',2,'PUT','/api/project/config/templates/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (108,'project:manage','模板删除',2,'DELETE','/api/project/config/templates/*',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (111,'project:view','项目查看（全员默认）',1,'MENU','project:view',NULL,'2026-10-28 15:52:04','2026-10-28 15:52:04');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (148,'project:view-all','项目全部可见（管理员豁免）',1,'MENU','project:view-all',NULL,'2026-10-28 17:52:33','2026-10-28 17:52:33');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (150,'announcement:manage','公告发布',2,'POST','/api/announcement',NULL,'2026-10-28 21:58:27','2026-10-28 21:58:27');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (151,'announcement:manage','公告编辑',2,'PUT','/api/announcement/*',NULL,'2026-10-28 21:58:27','2026-10-28 21:58:27');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (152,'announcement:manage','公告删除',2,'DELETE','/api/announcement/*',NULL,'2026-10-28 21:58:27','2026-10-28 21:58:27');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (153,'announcement:manage','公告上下架',2,'PUT','/api/announcement/*/status',NULL,'2026-10-28 21:58:27','2026-10-28 21:58:27');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (154,'announcement:manage','公告置顶',2,'PUT','/api/announcement/*/top',NULL,'2026-10-28 21:58:27','2026-10-28 21:58:27');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (155,'announcement:manage','公告已读统计',2,'GET','/api/announcement/*/read-stats',NULL,'2026-10-28 21:58:27','2026-10-28 21:58:27');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (156,'customer:manage','客户档案新增',2,'POST','/api/customer',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (157,'customer:manage','客户档案编辑',2,'PUT','/api/customer/**',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (158,'customer:manage','客户档案软删除',2,'DELETE','/api/customer/**',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (159,'customer:manage','客户档案恢复',2,'POST','/api/customer/*/restore',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (160,'customer:manage','客户附件上传',2,'POST','/api/customer/*/attachments',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (161,'customer:manage','客户附件删除',2,'DELETE','/api/customer/attachments/**',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (162,'customer:manage','客户关系建立',2,'POST','/api/customer/relations',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (163,'customer:manage','客户关系删除',2,'DELETE','/api/customer/relations/**',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (164,'customer:manage','客户关系类型配置',2,'POST','/api/customer/relations/types',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (165,'customer:manage','客户关系类型配置',2,'PUT','/api/customer/relations/types/**',NULL,'2026-10-29 10:18:09','2026-10-29 10:18:09');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (166,'customer:manage','客户管理',1,'MENU','customer:manage',NULL,'2026-10-29 16:23:59','2026-10-29 16:23:59');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (188,'supplier:create','供应商-新增',2,'POST','/api/supplier',NULL,'2026-10-31 16:54:23','2026-11-01 08:02:45');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (189,'supplier:manage','供应商-编辑',2,'PUT','/api/supplier/*',NULL,'2026-10-31 16:54:23','2026-10-31 16:54:23');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (190,'supplier:manage','供应商-删除',2,'DELETE','/api/supplier/*',NULL,'2026-10-31 16:54:23','2026-10-31 16:54:23');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (191,'supplier:image','供应商-图片上传',2,'POST','/api/supplier/*/images',NULL,'2026-10-31 16:54:23','2026-11-02 12:19:08');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (192,'supplier:manage','供应商-设为主图',2,'PUT','/api/supplier/*/images/*/main',NULL,'2026-10-31 16:54:23','2026-10-31 16:54:23');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (193,'supplier:manage','供应商-删除图片',2,'DELETE','/api/supplier/*/images/*',NULL,'2026-10-31 16:54:23','2026-10-31 16:54:23');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (194,'supplier:price','供应商-历史价格维护',2,'PUT','/api/supplier/*/prices',NULL,'2026-10-31 16:54:23','2026-11-02 12:19:08');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (195,'supplier:review','供应商-服务评价',2,'POST','/api/supplier/*/reviews',NULL,'2026-10-31 16:54:23','2026-11-09 10:50:47');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (196,'supplier:binding','供应商-绑定',2,'POST','/api/supplier/*/bindings',NULL,'2026-10-31 16:54:23','2026-11-01 08:02:45');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (197,'supplier:binding','供应商-解绑',2,'DELETE','/api/supplier/*/bindings/*',NULL,'2026-10-31 16:54:23','2026-11-01 08:02:45');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (200,'project:manage','项目-导出xlsx',2,'GET','/api/project/export',NULL,'2026-10-31 16:56:16','2026-10-31 16:56:16');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (201,'supplier:image','供应商-图片改名',2,'PUT','/api/supplier/*/images/*/name',NULL,'2026-11-01 08:02:45','2026-11-02 12:19:08');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (202,'supplier:manage','供应商-跨城批量解绑',2,'POST','/api/supplier/bindings/unbind-batch',NULL,'2026-11-01 08:02:45','2026-11-01 08:02:45');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (211,'project:manage','类型阶段序列保存',2,'PUT','/api/project/config/types/*/phases',NULL,'2026-11-02 11:18:32','2026-11-02 11:18:32');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (212,'project:manage','类型阶段模板绑定保存',2,'PUT','/api/project/config/types/*/phases/*/templates',NULL,'2026-11-02 11:18:32','2026-11-02 11:18:32');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (213,'project:manage','阶段模板绑定保存',2,'PUT','/api/project/config/phases/*/templates',NULL,'2026-11-02 11:18:32','2026-11-02 11:18:32');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (217,'approval:config','审批流配置-流程列表',2,'GET','/api/approval/config/list',NULL,'2026-11-02 21:40:17','2026-11-02 21:40:17');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (218,'approval:config','审批流配置-具体审批人列表',2,'GET','/api/approval/config/*/users',NULL,'2026-11-02 21:40:17','2026-11-02 21:40:17');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (219,'approval:config','审批流配置-具体审批人保存',2,'PUT','/api/approval/config/*/users',NULL,'2026-11-02 21:40:17','2026-11-02 21:40:17');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (220,'approval:audit','审批-退回修改',2,'POST','/api/approval/requests/*/return',NULL,'2026-11-02 21:40:17','2026-11-02 21:40:17');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (271,'storage:manage','存储管理',1,'MENU','storage:manage',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (272,'storage:manage','存储管理-统计预警',2,'GET','/api/storage-admin/stats',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (273,'storage:manage','存储管理-孤儿扫描',2,'GET','/api/storage-admin/project-orphans',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (274,'storage:manage','存储管理-孤儿文件清理',2,'POST','/api/storage-admin/project-orphans/purge',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (275,'storage:manage','存储管理-预览缓存清理',2,'POST','/api/storage-admin/preview-cache/purge',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (276,'storage:manage','存储管理-项目硬删预览',2,'GET','/api/storage-admin/projects/*/purge-preview',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (277,'storage:manage','存储管理-项目硬删除',2,'POST','/api/storage-admin/projects/*/purge',NULL,'2026-11-12 15:42:41','2026-11-12 15:42:41');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (278,'storage:manage','存储管理-孤儿扫描(全目录)',2,'GET','/api/storage-admin/orphans',NULL,'2026-11-12 16:03:46','2026-11-12 16:03:46');
INSERT IGNORE INTO `permission` (`id`, `code`, `name`, `type`, `method`, `path`, `parent_code`, `create_time`, `update_time`) VALUES (279,'storage:manage','存储管理-孤儿清理(全目录)',2,'POST','/api/storage-admin/orphans/purge',NULL,'2026-11-12 16:03:46','2026-11-12 16:03:46');
/*!40000 ALTER TABLE `permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `role_permission`
--

LOCK TABLES `role_permission` WRITE;
/*!40000 ALTER TABLE `role_permission` DISABLE KEYS */;
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (61,2,'expert:manage','2026-10-28 16:09:18');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (62,2,'audit:manage','2026-10-28 16:09:18');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (63,2,'approval:manage','2026-10-28 16:09:18');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (64,2,'approval:config','2026-10-28 16:09:18');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (65,2,'user:manage','2026-10-28 16:09:18');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (66,2,'project:view','2026-10-28 16:09:18');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (67,3,'approval:manage','2026-10-28 16:09:23');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (68,3,'project:view','2026-10-28 16:09:23');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (69,4,'project:view','2026-10-28 16:09:30');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (70,52,'project:view','2026-10-28 16:09:39');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (71,53,'project:view','2026-10-28 16:09:43');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (72,2,'project:manage','2026-10-28 16:56:15');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (73,53,'project:manage','2026-10-28 16:56:15');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (75,52,'knowledge:maintain','2026-10-28 16:56:41');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (78,2,'project:create','2026-10-28 17:52:33');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (79,3,'project:create','2026-10-28 17:52:33');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (80,4,'project:create','2026-10-28 17:52:33');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (81,52,'project:create','2026-10-28 17:52:33');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (82,53,'project:create','2026-10-28 17:52:33');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (85,2,'project:view-all','2026-10-28 17:52:33');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (86,2,'announcement:manage','2026-10-28 21:58:27');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (87,52,'customer:manage','2026-10-29 10:18:09');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (88,2,'customer:manage','2026-10-29 10:18:09');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (92,2,'supplier:manage','2026-10-31 16:54:23');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (93,2,'supplier:create','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (94,3,'supplier:create','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (95,4,'supplier:create','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (96,52,'supplier:create','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (97,53,'supplier:create','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (100,2,'supplier:binding','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (101,3,'supplier:binding','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (102,4,'supplier:binding','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (103,52,'supplier:binding','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (104,53,'supplier:binding','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (107,2,'supplier:dict','2026-11-01 08:02:45');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (108,2,'supplier:image','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (109,3,'supplier:image','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (110,4,'supplier:image','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (111,52,'supplier:image','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (112,53,'supplier:image','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (115,2,'supplier:price','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (116,3,'supplier:price','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (117,4,'supplier:price','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (118,52,'supplier:price','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (119,53,'supplier:price','2026-11-02 12:19:08');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (122,2,'approval:audit','2026-11-02 21:40:17');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (123,3,'approval:audit','2026-11-02 21:40:17');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (141,2,'supplier:review','2026-11-09 10:50:47');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (142,3,'supplier:review','2026-11-09 10:50:47');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (143,4,'supplier:review','2026-11-09 10:50:47');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (144,52,'supplier:review','2026-11-09 10:50:47');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (145,53,'supplier:review','2026-11-09 10:50:47');
INSERT IGNORE INTO `role_permission` (`id`, `role_id`, `permission_code`, `create_time`) VALUES (148,2,'storage:manage','2026-11-12 15:42:41');
/*!40000 ALTER TABLE `role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `project_type`
--

LOCK TABLES `project_type` WRITE;
/*!40000 ALTER TABLE `project_type` DISABLE KEYS */;
INSERT IGNORE INTO `project_type` (`id`, `name`, `sort`, `create_time`, `update_time`, `active_config_revision_id`) VALUES (1,'展会',5,'2026-10-28 15:52:03','2026-11-04 11:29:34',5);
INSERT IGNORE INTO `project_type` (`id`, `name`, `sort`, `create_time`, `update_time`, `active_config_revision_id`) VALUES (2,'会议',6,'2026-10-28 15:52:03','2026-11-04 11:29:34',6);
INSERT IGNORE INTO `project_type` (`id`, `name`, `sort`, `create_time`, `update_time`, `active_config_revision_id`) VALUES (3,'论坛',4,'2026-10-28 15:52:03','2026-11-04 11:29:34',4);
INSERT IGNORE INTO `project_type` (`id`, `name`, `sort`, `create_time`, `update_time`, `active_config_revision_id`) VALUES (6,'平台类项目',1,'2026-11-02 11:49:37','2026-11-04 11:29:34',1);
INSERT IGNORE INTO `project_type` (`id`, `name`, `sort`, `create_time`, `update_time`, `active_config_revision_id`) VALUES (7,'直付类项目',3,'2026-11-02 11:49:53','2026-11-04 11:29:34',3);
INSERT IGNORE INTO `project_type` (`id`, `name`, `sort`, `create_time`, `update_time`, `active_config_revision_id`) VALUES (8,'重点类项目',2,'2026-11-02 11:50:03','2026-11-04 11:29:34',2);
/*!40000 ALTER TABLE `project_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `project_phase`
--

LOCK TABLES `project_phase` WRITE;
/*!40000 ALTER TABLE `project_phase` DISABLE KEYS */;
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (1,'立项',1,0,'2026-10-28 15:52:03');
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (2,'预算',2,0,'2026-10-28 15:52:03');
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (3,'筹备',3,0,'2026-10-28 15:52:03');
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (4,'进行中',4,0,'2026-10-28 15:52:03');
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (5,'结算',5,0,'2026-10-28 15:52:03');
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (6,'已完成',6,0,'2026-10-28 15:52:03');
INSERT IGNORE INTO `project_phase` (`id`, `name`, `sort`, `is_active`, `create_time`) VALUES (8,'日程上传',99,1,'2026-11-09 12:57:06');
/*!40000 ALTER TABLE `project_phase` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `project_type_phase_rel`
--

LOCK TABLES `project_type_phase_rel` WRITE;
/*!40000 ALTER TABLE `project_type_phase_rel` DISABLE KEYS */;
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (6,6,1,0,'2026-11-02 13:34:02');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (7,6,2,1,'2026-11-02 13:34:02');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (8,6,3,2,'2026-11-02 13:34:02');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (9,6,4,3,'2026-11-02 13:34:02');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (10,6,5,4,'2026-11-02 13:34:02');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (11,8,1,1,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (12,8,2,2,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (13,8,3,3,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (14,8,4,4,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (15,8,5,5,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (16,8,6,6,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (17,7,1,1,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (18,7,2,2,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (19,7,3,3,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (20,7,4,4,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (21,7,5,5,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (22,7,6,6,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (23,3,1,1,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (24,3,2,2,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (25,3,3,3,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (26,3,4,4,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (27,3,5,5,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (28,3,6,6,'2026-11-04 10:59:54');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (29,1,1,1,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (30,1,2,2,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (31,1,3,3,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (32,1,4,4,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (33,1,5,5,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (34,1,6,6,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (35,2,1,1,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (36,2,2,2,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (37,2,3,3,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (38,2,4,4,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (39,2,5,5,'2026-11-04 10:59:55');
INSERT IGNORE INTO `project_type_phase_rel` (`id`, `type_id`, `phase_id`, `sort`, `create_time`) VALUES (40,2,6,6,'2026-11-04 10:59:55');
/*!40000 ALTER TABLE `project_type_phase_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `project_phase_template_rel`
--

LOCK TABLES `project_phase_template_rel` WRITE;
/*!40000 ALTER TABLE `project_phase_template_rel` DISABLE KEYS */;
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (15,1,5,0,1,0,'2026-11-02 09:14:08');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (16,2,4,0,1,0,'2026-11-02 09:14:16');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (17,3,1,0,0,0,'2026-11-02 09:14:20');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (18,3,2,0,0,1,'2026-11-02 09:14:20');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (19,3,3,0,0,2,'2026-11-02 09:14:20');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (20,4,1,0,0,0,'2026-11-02 09:14:23');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (21,5,1,0,0,0,'2026-11-02 09:14:26');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (24,1,1,6,0,0,'2026-11-02 16:00:55');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (25,1,2,6,1,1,'2026-11-02 16:00:55');
INSERT IGNORE INTO `project_phase_template_rel` (`id`, `phase_id`, `template_id`, `type_id`, `required`, `sort`, `create_time`) VALUES (26,1,5,6,1,2,'2026-11-02 16:00:55');
/*!40000 ALTER TABLE `project_phase_template_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `project_template`
--

LOCK TABLES `project_template` WRITE;
/*!40000 ALTER TABLE `project_template` DISABLE KEYS */;
INSERT IGNORE INTO `project_template` (`id`, `name`, `template_type`, `create_by`, `create_time`) VALUES (1,'资料附件','FILE','SYSTEM','2026-10-28 15:52:03');
INSERT IGNORE INTO `project_template` (`id`, `name`, `template_type`, `create_by`, `create_time`) VALUES (2,'专家','EXPERT','SYSTEM','2026-10-28 15:52:03');
INSERT IGNORE INTO `project_template` (`id`, `name`, `template_type`, `create_by`, `create_time`) VALUES (3,'供应商','SUPPLIER','SYSTEM','2026-10-28 15:52:03');
INSERT IGNORE INTO `project_template` (`id`, `name`, `template_type`, `create_by`, `create_time`) VALUES (4,'预算','BUDGET','SYSTEM','2026-10-28 15:52:03');
INSERT IGNORE INTO `project_template` (`id`, `name`, `template_type`, `create_by`, `create_time`) VALUES (5,'自定义文本','RICH_TEXT','SYSTEM','2026-10-28 15:52:03');
/*!40000 ALTER TABLE `project_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `supplier_type`
--

LOCK TABLES `supplier_type` WRITE;
/*!40000 ALTER TABLE `supplier_type` DISABLE KEYS */;
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (1,'HOTEL','酒店',1,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (2,'PRINT','打印店',2,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (3,'MEDIA','媒体（视频/微站）',3,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (4,'IMAGING','影像（设备/大屏/舞台）',4,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (5,'FLEET','车队',5,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (6,'TICKET','票务（机票/高铁）',6,'2026-10-26 19:48:20','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_type` (`id`, `biz_key`, `name`, `sort`, `create_time`, `update_time`) VALUES (9,NULL,'_review_tmp',0,'2026-11-01 08:46:27','2026-11-01 08:46:27');
/*!40000 ALTER TABLE `supplier_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `supplier_city`
--

LOCK TABLES `supplier_city` WRITE;
/*!40000 ALTER TABLE `supplier_city` DISABLE KEYS */;
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (1,'北京市','北京市','110000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (2,'天津市','天津市','120000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (3,'上海市','上海市','310000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (4,'重庆市','重庆市','500000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (5,'河北省','石家庄市','130100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (6,'河北省','唐山市','130200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (7,'河北省','秦皇岛市','130300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (8,'河北省','邯郸市','130400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (9,'河北省','邢台市','130500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (10,'河北省','保定市','130600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (11,'河北省','张家口市','130700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (12,'河北省','承德市','130800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (13,'河北省','沧州市','130900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (14,'河北省','廊坊市','131000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (15,'河北省','衡水市','131100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (16,'山西省','太原市','140100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (17,'山西省','大同市','140200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (18,'山西省','阳泉市','140300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (19,'山西省','长治市','140400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (20,'山西省','晋城市','140500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (21,'山西省','朔州市','140600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (22,'山西省','晋中市','140700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (23,'山西省','运城市','140800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (24,'山西省','忻州市','140900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (25,'山西省','临汾市','141000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (26,'山西省','吕梁市','141100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (27,'内蒙古自治区','呼和浩特市','150100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (28,'内蒙古自治区','包头市','150200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (29,'内蒙古自治区','乌海市','150300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (30,'内蒙古自治区','赤峰市','150400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (31,'内蒙古自治区','通辽市','150500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (32,'内蒙古自治区','鄂尔多斯市','150600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (33,'内蒙古自治区','呼伦贝尔市','150700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (34,'内蒙古自治区','巴彦淖尔市','150800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (35,'内蒙古自治区','乌兰察布市','150900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (36,'内蒙古自治区','兴安盟','152200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (37,'内蒙古自治区','锡林郭勒盟','152500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (38,'内蒙古自治区','阿拉善盟','152900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (39,'辽宁省','沈阳市','210100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (40,'辽宁省','大连市','210200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (41,'辽宁省','鞍山市','210300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (42,'辽宁省','抚顺市','210400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (43,'辽宁省','本溪市','210500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (44,'辽宁省','丹东市','210600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (45,'辽宁省','锦州市','210700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (46,'辽宁省','营口市','210800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (47,'辽宁省','阜新市','210900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (48,'辽宁省','辽阳市','211000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (49,'辽宁省','盘锦市','211100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (50,'辽宁省','铁岭市','211200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (51,'辽宁省','朝阳市','211300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (52,'辽宁省','葫芦岛市','211400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (53,'吉林省','长春市','220100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (54,'吉林省','吉林市','220200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (55,'吉林省','四平市','220300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (56,'吉林省','辽源市','220400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (57,'吉林省','通化市','220500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (58,'吉林省','白山市','220600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (59,'吉林省','松原市','220700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (60,'吉林省','白城市','220800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (61,'吉林省','延边朝鲜族自治州','222400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (62,'黑龙江省','哈尔滨市','230100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (63,'黑龙江省','齐齐哈尔市','230200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (64,'黑龙江省','鸡西市','230300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (65,'黑龙江省','鹤岗市','230400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (66,'黑龙江省','双鸭山市','230500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (67,'黑龙江省','大庆市','230600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (68,'黑龙江省','伊春市','230700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (69,'黑龙江省','佳木斯市','230800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (70,'黑龙江省','七台河市','230900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (71,'黑龙江省','牡丹江市','231000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (72,'黑龙江省','黑河市','231100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (73,'黑龙江省','绥化市','231200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (74,'黑龙江省','大兴安岭地区','232700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (75,'江苏省','南京市','320100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (76,'江苏省','无锡市','320200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (77,'江苏省','徐州市','320300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (78,'江苏省','常州市','320400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (79,'江苏省','苏州市','320500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (80,'江苏省','南通市','320600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (81,'江苏省','连云港市','320700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (82,'江苏省','淮安市','320800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (83,'江苏省','盐城市','320900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (84,'江苏省','扬州市','321000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (85,'江苏省','镇江市','321100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (86,'江苏省','泰州市','321200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (87,'江苏省','宿迁市','321300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (88,'浙江省','杭州市','330100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (89,'浙江省','宁波市','330200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (90,'浙江省','温州市','330300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (91,'浙江省','嘉兴市','330400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (92,'浙江省','湖州市','330500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (93,'浙江省','绍兴市','330600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (94,'浙江省','金华市','330700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (95,'浙江省','衢州市','330800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (96,'浙江省','舟山市','330900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (97,'浙江省','台州市','331000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (98,'浙江省','丽水市','331100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (99,'安徽省','合肥市','340100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (100,'安徽省','芜湖市','340200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (101,'安徽省','蚌埠市','340300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (102,'安徽省','淮南市','340400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (103,'安徽省','马鞍山市','340500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (104,'安徽省','淮北市','340600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (105,'安徽省','铜陵市','340700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (106,'安徽省','安庆市','340800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (107,'安徽省','黄山市','341000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (108,'安徽省','滁州市','341100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (109,'安徽省','阜阳市','341200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (110,'安徽省','宿州市','341300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (111,'安徽省','六安市','341500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (112,'安徽省','亳州市','341600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (113,'安徽省','池州市','341700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (114,'安徽省','宣城市','341800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (115,'福建省','福州市','350100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (116,'福建省','厦门市','350200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (117,'福建省','莆田市','350300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (118,'福建省','三明市','350400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (119,'福建省','泉州市','350500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (120,'福建省','漳州市','350600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (121,'福建省','南平市','350700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (122,'福建省','龙岩市','350800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (123,'福建省','宁德市','350900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (124,'江西省','南昌市','360100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (125,'江西省','景德镇市','360200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (126,'江西省','萍乡市','360300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (127,'江西省','九江市','360400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (128,'江西省','新余市','360500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (129,'江西省','鹰潭市','360600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (130,'江西省','赣州市','360700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (131,'江西省','吉安市','360800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (132,'江西省','宜春市','360900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (133,'江西省','抚州市','361000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (134,'江西省','上饶市','361100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (135,'山东省','济南市','370100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (136,'山东省','青岛市','370200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (137,'山东省','淄博市','370300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (138,'山东省','枣庄市','370400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (139,'山东省','东营市','370500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (140,'山东省','烟台市','370600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (141,'山东省','潍坊市','370700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (142,'山东省','济宁市','370800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (143,'山东省','泰安市','370900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (144,'山东省','威海市','371000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (145,'山东省','日照市','371100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (146,'山东省','临沂市','371300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (147,'山东省','德州市','371400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (148,'山东省','聊城市','371500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (149,'山东省','滨州市','371600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (150,'山东省','菏泽市','371700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (151,'河南省','郑州市','410100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (152,'河南省','开封市','410200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (153,'河南省','洛阳市','410300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (154,'河南省','平顶山市','410400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (155,'河南省','安阳市','410500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (156,'河南省','鹤壁市','410600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (157,'河南省','新乡市','410700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (158,'河南省','焦作市','410800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (159,'河南省','濮阳市','410900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (160,'河南省','许昌市','411000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (161,'河南省','漯河市','411100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (162,'河南省','三门峡市','411200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (163,'河南省','南阳市','411300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (164,'河南省','商丘市','411400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (165,'河南省','信阳市','411500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (166,'河南省','周口市','411600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (167,'河南省','驻马店市','411700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (168,'河南省','济源市','419001','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (169,'湖北省','武汉市','420100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (170,'湖北省','黄石市','420200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (171,'湖北省','十堰市','420300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (172,'湖北省','宜昌市','420500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (173,'湖北省','襄阳市','420600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (174,'湖北省','鄂州市','420700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (175,'湖北省','荆门市','420800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (176,'湖北省','孝感市','420900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (177,'湖北省','荆州市','421000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (178,'湖北省','黄冈市','421100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (179,'湖北省','咸宁市','421200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (180,'湖北省','随州市','421300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (181,'湖北省','恩施土家族苗族自治州','422800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (182,'湖北省','仙桃市','429004','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (183,'湖北省','潜江市','429005','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (184,'湖北省','天门市','429006','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (185,'湖南省','长沙市','430100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (186,'湖南省','株洲市','430200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (187,'湖南省','湘潭市','430300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (188,'湖南省','衡阳市','430400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (189,'湖南省','邵阳市','430500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (190,'湖南省','岳阳市','430600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (191,'湖南省','常德市','430700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (192,'湖南省','张家界市','430800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (193,'湖南省','益阳市','430900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (194,'湖南省','郴州市','431000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (195,'湖南省','永州市','431100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (196,'湖南省','怀化市','431200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (197,'湖南省','娄底市','431300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (198,'湖南省','湘西土家族苗族自治州','433100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (199,'广东省','广州市','440100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (200,'广东省','韶关市','440200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (201,'广东省','深圳市','440300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (202,'广东省','珠海市','440400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (203,'广东省','汕头市','440500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (204,'广东省','佛山市','440600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (205,'广东省','江门市','440700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (206,'广东省','湛江市','440800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (207,'广东省','茂名市','440900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (208,'广东省','肇庆市','441200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (209,'广东省','惠州市','441300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (210,'广东省','梅州市','441400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (211,'广东省','汕尾市','441500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (212,'广东省','河源市','441600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (213,'广东省','阳江市','441700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (214,'广东省','清远市','441800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (215,'广东省','东莞市','441900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (216,'广东省','中山市','442000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (217,'广东省','潮州市','445100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (218,'广东省','揭阳市','445200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (219,'广东省','云浮市','445300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (220,'广西壮族自治区','南宁市','450100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (221,'广西壮族自治区','柳州市','450200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (222,'广西壮族自治区','桂林市','450300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (223,'广西壮族自治区','梧州市','450400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (224,'广西壮族自治区','北海市','450500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (225,'广西壮族自治区','防城港市','450600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (226,'广西壮族自治区','钦州市','450700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (227,'广西壮族自治区','贵港市','450800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (228,'广西壮族自治区','玉林市','450900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (229,'广西壮族自治区','百色市','451000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (230,'广西壮族自治区','贺州市','451100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (231,'广西壮族自治区','河池市','451200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (232,'广西壮族自治区','来宾市','451300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (233,'广西壮族自治区','崇左市','451400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (234,'海南省','海口市','460100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (235,'海南省','三亚市','460200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (236,'海南省','三沙市','460300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (237,'海南省','儋州市','460400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (238,'四川省','成都市','510100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (239,'四川省','自贡市','510300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (240,'四川省','攀枝花市','510400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (241,'四川省','泸州市','510500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (242,'四川省','德阳市','510600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (243,'四川省','绵阳市','510700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (244,'四川省','广元市','510800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (245,'四川省','遂宁市','510900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (246,'四川省','内江市','511000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (247,'四川省','乐山市','511100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (248,'四川省','南充市','511300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (249,'四川省','眉山市','511400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (250,'四川省','宜宾市','511500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (251,'四川省','广安市','511600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (252,'四川省','达州市','511700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (253,'四川省','雅安市','511800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (254,'四川省','巴中市','511900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (255,'四川省','资阳市','512000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (256,'四川省','阿坝藏族羌族自治州','513200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (257,'四川省','甘孜藏族自治州','513300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (258,'四川省','凉山彝族自治州','513400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (259,'贵州省','贵阳市','520100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (260,'贵州省','六盘水市','520200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (261,'贵州省','遵义市','520300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (262,'贵州省','安顺市','520400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (263,'贵州省','毕节市','520500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (264,'贵州省','铜仁市','520600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (265,'贵州省','黔西南布依族苗族自治州','522300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (266,'贵州省','黔东南苗族侗族自治州','522600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (267,'贵州省','黔南布依族苗族自治州','522700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (268,'云南省','昆明市','530100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (269,'云南省','曲靖市','530300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (270,'云南省','玉溪市','530400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (271,'云南省','保山市','530500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (272,'云南省','昭通市','530600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (273,'云南省','丽江市','530700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (274,'云南省','普洱市','530800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (275,'云南省','临沧市','530900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (276,'云南省','楚雄彝族自治州','532300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (277,'云南省','红河哈尼族彝族自治州','532500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (278,'云南省','文山壮族苗族自治州','532600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (279,'云南省','西双版纳傣族自治州','532800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (280,'云南省','大理白族自治州','532900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (281,'云南省','德宏傣族景颇族自治州','533100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (282,'云南省','怒江傈僳族自治州','533300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (283,'云南省','迪庆藏族自治州','533400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (284,'西藏自治区','拉萨市','540100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (285,'西藏自治区','日喀则市','540200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (286,'西藏自治区','昌都市','540300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (287,'西藏自治区','林芝市','540400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (288,'西藏自治区','山南市','540500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (289,'西藏自治区','那曲市','540600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (290,'西藏自治区','阿里地区','542500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (291,'陕西省','西安市','610100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (292,'陕西省','铜川市','610200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (293,'陕西省','宝鸡市','610300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (294,'陕西省','咸阳市','610400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (295,'陕西省','渭南市','610500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (296,'陕西省','延安市','610600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (297,'陕西省','汉中市','610700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (298,'陕西省','榆林市','610800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (299,'陕西省','安康市','610900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (300,'陕西省','商洛市','611000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (301,'甘肃省','兰州市','620100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (302,'甘肃省','嘉峪关市','620200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (303,'甘肃省','金昌市','620300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (304,'甘肃省','白银市','620400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (305,'甘肃省','天水市','620500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (306,'甘肃省','武威市','620600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (307,'甘肃省','张掖市','620700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (308,'甘肃省','平凉市','620800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (309,'甘肃省','酒泉市','620900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (310,'甘肃省','庆阳市','621000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (311,'甘肃省','定西市','621100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (312,'甘肃省','陇南市','621200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (313,'甘肃省','临夏回族自治州','622900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (314,'甘肃省','甘南藏族自治州','623000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (315,'青海省','西宁市','630100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (316,'青海省','海东市','630200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (317,'青海省','海北藏族自治州','632200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (318,'青海省','黄南藏族自治州','632300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (319,'青海省','海南藏族自治州','632500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (320,'青海省','果洛藏族自治州','632600','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (321,'青海省','玉树藏族自治州','632700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (322,'青海省','海西蒙古族藏族自治州','632800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (323,'宁夏回族自治区','银川市','640100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (324,'宁夏回族自治区','石嘴山市','640200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (325,'宁夏回族自治区','吴忠市','640300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (326,'宁夏回族自治区','固原市','640400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (327,'宁夏回族自治区','中卫市','640500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (328,'新疆维吾尔自治区','乌鲁木齐市','650100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (329,'新疆维吾尔自治区','克拉玛依市','650200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (330,'新疆维吾尔自治区','吐鲁番市','650400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (331,'新疆维吾尔自治区','哈密市','650500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (332,'新疆维吾尔自治区','昌吉回族自治州','652300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (333,'新疆维吾尔自治区','博尔塔拉蒙古自治州','652700','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (334,'新疆维吾尔自治区','巴音郭楞蒙古自治州','652800','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (335,'新疆维吾尔自治区','阿克苏地区','652900','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (336,'新疆维吾尔自治区','克孜勒苏柯尔克孜自治州','653000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (337,'新疆维吾尔自治区','喀什地区','653100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (338,'新疆维吾尔自治区','和田地区','653200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (339,'新疆维吾尔自治区','伊犁哈萨克自治州','654000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (340,'新疆维吾尔自治区','塔城地区','654200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (341,'新疆维吾尔自治区','阿勒泰地区','654300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (342,'香港特别行政区','香港','810000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (343,'澳门特别行政区','澳门','820000','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (344,'台湾省','台北市','710100','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (345,'台湾省','新北市','710200','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (346,'台湾省','桃园市','710300','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (347,'台湾省','台中市','710400','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (348,'台湾省','台南市','710500','2026-10-26 19:48:20');
INSERT IGNORE INTO `supplier_city` (`id`, `province`, `name`, `code`, `create_time`) VALUES (349,'台湾省','高雄市','710600','2026-10-26 19:48:20');
/*!40000 ALTER TABLE `supplier_city` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `customer_relation_type`
--

LOCK TABLES `customer_relation_type` WRITE;
/*!40000 ALTER TABLE `customer_relation_type` DISABLE KEYS */;
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (1,'introduction','介绍关系',1,1,'DIRECTED',NULL,'2026-10-29 10:18:09');
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (2,'superior-subordinate','上下级',1,1,'DIRECTED',NULL,'2026-10-29 10:18:09');
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (3,'colleague','同事',1,1,'UNDIRECTED',NULL,'2026-10-29 10:18:09');
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (4,'business-partner','业务伙伴',1,1,'BIDIRECTED',NULL,'2026-10-29 10:18:09');
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (5,'friend-relative','亲友',1,1,'UNDIRECTED',NULL,'2026-10-29 10:18:09');
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (6,'custom_1793244461962','客户介绍',0,0,'DIRECTED','jshz','2026-10-29 11:27:41');
INSERT IGNORE INTO `customer_relation_type` (`id`, `code`, `name`, `builtin`, `status`, `direction`, `create_by`, `create_time`) VALUES (7,'custom_1793253190513','客户介绍',0,0,'DIRECTED','王创','2026-10-29 13:53:10');
/*!40000 ALTER TABLE `customer_relation_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `approval_config`
--

LOCK TABLES `approval_config` WRITE;
/*!40000 ALTER TABLE `approval_config` DISABLE KEYS */;
INSERT IGNORE INTO `approval_config` (`id`, `flow_key`, `flow_name`, `enabled`, `approver_role`, `create_time`, `update_time`) VALUES (2,'EXPERT','专家信息修改审批',1,'APPROVER','2026-11-02 21:35:29','2026-11-02 21:35:29');
INSERT IGNORE INTO `approval_config` (`id`, `flow_key`, `flow_name`, `enabled`, `approver_role`, `create_time`, `update_time`) VALUES (3,'CONTRACT','项目合同审批',1,'APPROVER','2026-11-02 21:40:16','2026-11-02 21:40:16');
/*!40000 ALTER TABLE `approval_config` ENABLE KEYS */;
UNLOCK TABLES;

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ------------------------------------------------------------
-- 三、后续变更约定
-- ------------------------------------------------------------
-- 1. 任何结构/权限/字典变更，都在 database\migration\ 新增 <yyyyMMdd>_<描述>.sql（幂等）；
-- 2. 执行并登记：.\scripts\db-migrate.ps1 -Apply（自动计算 SHA256 写入 schema_migration）；
-- 3. 查看状态：.\scripts\db-migrate.ps1 -Status（已执行 / 待执行 / 脚本漂移）；
-- 4. 禁止直接改生产库表结构而不留脚本，否则结构漂移无法追溯。
-- ------------------------------------------------------------
-- 四、基线补齐（2026-11-16）
-- ------------------------------------------------------------
-- init.sql 是「新环境唯一 DDL 来源」。本节把 2026-11-12 基线之后、已由
-- database\migration\20261116_*.sql 落地的结构同步进基线，使 `mysql < init.sql`
-- 后的库结构与生产一致（新环境无需再重放历史迁移）。
-- 来源迁移：20261116_ai_assistant.sql / 20261116_budget_stage.sql / 20261116_budget_fee_scene.sql
-- 一致性校验：.\scripts\check-schema-parity.ps1（init.sql 与生产库表清单必须一致）

-- ---------- 4.1 AI 助手知识条目 ----------
CREATE TABLE IF NOT EXISTS `ai_assistant_entry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `entry_key` VARCHAR(50) NOT NULL COMMENT '稳定键',
  `title` VARCHAR(200) NOT NULL COMMENT '条目标题',
  `questions` TEXT NULL COMMENT '问法样例，每行一条（意图匹配用）',
  `answer` TEXT NOT NULL COMMENT '答案正文（模型仅润色，不生成链接/步骤）',
  `link_url` VARCHAR(255) NULL COMMENT '跳转深链（站内路由）',
  `link_label` VARCHAR(100) NULL COMMENT '跳转按钮文案',
  `steps` TEXT NULL COMMENT '操作步骤，每行一步',
  `perm_code` VARCHAR(50) NULL COMMENT '所需权限码；NULL=全员可见',
  `sort` INT NOT NULL DEFAULT 0,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_aae_key` (`entry_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 助手知识条目';

INSERT IGNORE INTO `ai_assistant_entry` (`entry_key`,`title`,`questions`,`answer`,`link_url`,`link_label`,`steps`,`perm_code`,`sort`) VALUES
('CREATE_PROJECT','新建项目','怎么新建项目\n如何立项\n添加一个新项目\n建项目\n帮我新建项目','在项目管理页点击「新建项目」，填写项目名称、会议大类（线上/线下）、团队、项目起止时间、举办时间等信息后保存。新项目默认从「会议预算制作」阶段开始推进。','/project?action=create','去新建项目','打开项目管理页\n点击「新建项目」按钮\n填写名称/大类/团队/起止时间等必填项\n保存后进入项目详情，从会议预算制作阶段开始',NULL,1),
('EDIT_PROJECT','编辑项目信息','怎么修改项目信息\n改项目名称\n编辑项目','在项目详情页点击「编辑项目」可修改基础信息。项目成员（负责人/创建人/协助人）均可编辑。','/project','去项目管理','进入项目详情页\n点击右上角「编辑项目」\n修改后保存',NULL,2),
('PHASE_ADVANCE','项目阶段推进','怎么推进阶段\n项目到下一阶段\n阶段回退','项目详情页顶部有生命周期步骤条，点击「推进下一阶段」进入下一阶段；如需回退可用「更多操作」中的回退入口。当前流程：会议预算制作 → 日程上传。','/project','去项目管理','进入项目详情页\n确认当前阶段任务已完成\n点击「推进下一阶段」',NULL,3),
('BUDGET_OFFLINE','线下会议预算填写','线下预算怎么填\n预算明细怎么录\n会议费用怎么报','线下项目在「会议预算制作」阶段按七类费用填写：酒店（场地/住宿/用餐/茶歇）、交通（大交通+地面交通）、搭建（非必需）、物料、媒体（非必需）、礼品、人员。每行填数量×单价自动算金额，可增删行；填完点「确认报价」。收费标准默认值已预填，可修改。','/project','去项目管理','进入线下项目详情（会议预算制作阶段）\n按分节填写数量与单价\n非必需分节可整节移除\n核对合计后点「确认报价」',NULL,4),
('BUDGET_ONLINE','线上项目报价单','线上报价怎么做\n线上项目预算\n点选报价','线上项目在「会议预算制作」阶段直接点选收费项生成报价单：勾选需要的收费项即计入合计，数量可调整，也可添加自定义费用项，确认后生成报价单。收费项由管理员在「项目配置-线上收费标准」维护。','/project','去项目管理','进入线上项目详情（会议预算制作阶段）\n勾选需要的收费项\n可调整数量或添加自定义项\n点「确认报价」',NULL,5),
('FEE_CONFIG','收费标准配置','收费标准在哪改\n串场费怎么调\n预算默认单价修改\n线上收费项配置','管理员在「管理员设置→项目配置」维护收费标准，分线下/线上两个页签。可修改默认值（区间填「低,高」）、单位、备注，可启停或新增收费项。修改只影响之后新打开的预算单。','/admin/project/config','去项目配置','打开管理员设置→项目配置\n选择线下/线上页签\n修改或新增收费项\n点「保存修改」','project:manage',6),
('SCHEDULE_UPLOAD','日程上传与解析','日程怎么传\n上传会议日程\n议程解析','在项目的「日程上传」阶段上传日程文件（pdf/doc/docx/ppt/pptx/图片/xlsx，≤50MB），系统自动解析参会专家为候选名单，需人工逐条确认后保存。重新上传会替换当前日程并重新解析。','/project','去项目管理','进入项目详情并推进到日程上传阶段\n在阶段资料区上传日程文件\n等待自动解析\n在参会专家区逐条确认候选并保存',NULL,7),
('SPEAKER_CONFIRM','参会专家确认与建档','参会专家怎么确认\n库外专家建档\n专家名单确认','日程解析出的专家为候选，需在参会专家区确认：能匹配专家库的直接关联，库外专家可「去建档」或批量上传简历建档后自动回绑。确认后成为正式记录。','/project','去项目管理','进入项目详情参会专家区\n核对候选名单与专家库匹配\n库外专家点「去建档」或用批量建档\n点「确认保存参会专家」',NULL,8),
('EXPERT_SEARCH','专家库查询','怎么查专家\n搜索专家\n专家库在哪','专家库支持关键词搜索与高级筛选（地区/职称/单位等），提供列表与卡片双视图，可查看详情、简历与联系方式。','/experts','去专家库','打开专家库\n输入关键词或使用高级筛选\n点击专家查看详情',NULL,9),
('EXPERT_RESUME','专家简历上传解析','简历怎么传\n批量上传简历\n简历解析','专家库支持上传简历自动解析建档（姓名/单位/职称/简介等），支持批量上传；解析结果为候选，需人工核对后保存。项目内也可在参会专家区批量建档。','/experts','去专家库','打开专家库\n点击上传简历（支持批量）\n核对解析结果\n确认保存建档',NULL,10),
('COMPLIANCE_NOTEBOOK','合规记事本','合规待办怎么处理\n合规记事本在哪\n合规要求注意事项','项目详情右侧「合规要求注意事项」展示该项目绑定的合规待办，可标记完成/待处理；企业合规板块支持文档解析生成事项草稿（需人工确认）、收藏关注与个人进度跟踪。','/knowledge-planet/enterprise-compliance','去企业合规','打开项目详情查看右侧合规待办\n或进入企业合规板块管理事项\n处理完成后标记状态',NULL,11),
('KNOWLEDGE_UPLOAD','知识文档上传','知识星球怎么传文档\n上传规范文档\n知识库上传','知识星球支持上传文档（pdf/office/图片等），系统解析入库后可全文检索与在线预览；文档有版本管理，删除会同步索引。','/knowledge-planet','去知识星球','打开知识星球\n点击上传文档\n等待解析完成\n可检索/预览/管理版本',NULL,12),
('KNOWLEDGE_SEARCH','知识检索与AI问答','怎么搜知识文档\n知识库搜索\n文档内容查询','知识星球支持关键词检索与语义问答，检索结果遵守文档权限；点击结果可在线预览原文。','/knowledge-planet','去知识星球','打开知识星球\n输入关键词或问题\n查看结果并预览原文',NULL,13),
('APPROVAL_FLOW','审批流程','审批怎么发起\n审批流配置\n审批在哪看','系统内置审批流（如项目相关审批），可在审批板块查看待办与历史；管理员可在「审批流配置」调整审批节点与审批人。','/profile/my-approvals','去我的审批','打开审批板块查看待办\n按流程提交/审批\n管理员可在审批流配置调整节点',NULL,14),
('DASHBOARD_LAYOUT','工作台布局调整','工作台怎么自定义\n首页卡片拖动\n工作台布局','工作台支持拖拽调整卡片布局，布局按用户保存；登录后可在卡片目录添加/移除卡片。','/dashboard','去工作台','打开工作台\n进入编辑模式拖动卡片\n保存布局',NULL,15),
('LOGIN_PASSWORD','登录与改密','怎么登录\n忘记密码\n修改密码','使用账号密码登录；首次登录或管理员重置后需修改密码。忘记密码请联系管理员重置。',NULL,NULL,'使用分配的账号密码登录\n按提示修改初始密码\n忘记密码联系管理员重置',NULL,16),
('CUSTOMER_SUPPLIER','客户与供应商管理','客户信息在哪\n供应商查询\n供应商报价','客户与供应商模块支持档案维护、关联项目查看；供应商含报价项与图片资料，支持列表/详情视图。','/supplier','去供应商','打开客户/供应商模块\n搜索或新建档案\n查看详情与关联信息',NULL,17),
('STORAGE_ADMIN','存储管理','磁盘占用查看\n孤儿文件清理\n存储管理','管理员存储管理工具：查看各存储目录占用与预警、扫描清理孤儿文件、清理预览缓存。硬删除项目需输入完整项目名确认。','/admin/storage','去存储管理','打开管理员设置→存储管理\n查看占用统计\n按需扫描/清理孤儿文件','storage:manage',18),
('USER_ROLE_ADMIN','用户与角色管理','怎么加用户\n角色权限配置\n给用户开权限','管理员在用户管理新增/停用用户，在角色管理配置角色权限码；权限为数据驱动，接口与菜单按权限码控制。','/admin/users','去用户管理','打开管理员设置→用户管理/角色管理\n新增用户或调整角色权限\n用户重新登录生效','user:manage',19),
('PROJECT_FILE_PREVIEW','项目文件预览下载','项目文件怎么预览\n文件打不开\n下载项目附件','项目详情右侧「项目文件」列出各阶段上传的文件，点击文件名在线预览（pdf/图片/Office 转 PDF），也可下载。','/project','去项目管理','进入项目详情\n右侧项目文件区点击文件名预览\n或点下载',NULL,20);

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('ai:manage', 'AI助手知识库', 1, 'MENU', 'ai:manage');
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('ai:manage', 'AI助手-条目列表', 2, 'GET', '/api/ai-assistant/entries'),
('ai:manage', 'AI助手-条目保存', 2, 'PUT', '/api/ai-assistant/entries');
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'ai:manage' FROM `role` r WHERE r.`code` = 'ADMIN';

-- ---------- 4.2 会议预算制作阶段与预算三表 ----------
-- 内置阶段「会议预算制作」（sort=98，位于日程上传之前；幂等）
INSERT INTO `project_phase` (`name`, `sort`, `is_active`)
SELECT '会议预算制作', 98, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `project_phase` WHERE `name` = '会议预算制作');

CREATE TABLE IF NOT EXISTS `project_budget` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` BIGINT NOT NULL COMMENT '项目 id',
  `category` VARCHAR(20) NOT NULL COMMENT '大类快照：ONLINE 线上 / OFFLINE 线下',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / CONFIRMED 已确认',
  `total_amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '明细合计',
  `estimate_inputs` JSON NULL,
  `remark` VARCHAR(1000) NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_pb_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目会议预算/报价单';

CREATE TABLE IF NOT EXISTS project_budget_quote (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  request_key VARCHAR(36) NOT NULL,
  doc_id BIGINT NOT NULL,
  snapshot JSON NOT NULL,
  client_note VARCHAR(1000),
  created_by BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  customer_name VARCHAR(100),
  customer_confirmed_at DATETIME,
  customer_remark VARCHAR(1000),
  evidence_doc_id BIGINT,
  recorded_by BIGINT,
  recorded_at DATETIME,
  UNIQUE KEY uk_budget_quote_version (project_id, version_no),
  UNIQUE KEY uk_budget_quote_request (project_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `project_budget_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `budget_id` BIGINT NOT NULL,
  `project_id` BIGINT NOT NULL COMMENT '冗余便于按项目查',
  `section` VARCHAR(20) NOT NULL COMMENT 'HOTEL 酒店/TRAFFIC 交通/BUILD 搭建/MATERIAL 物料/MEDIA 媒体/GIFT 礼品/STAFF 人员/ONLINE_QUOTE 线上报价',
  `item_key` VARCHAR(50) NULL COMMENT '收费标准 fee_key 关联（预填项）；NULL=手工行',
  `name` VARCHAR(200) NOT NULL COMMENT '费用项名称',
  `quantity` DECIMAL(10,2) NOT NULL DEFAULT 1,
  `unit` VARCHAR(50) NULL COMMENT '场/人/天/张/个等',
  `unit_price` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT 'quantity*unit_price（服务端计算）',
  `remark` VARCHAR(500) NULL,
  `sort` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_pbi_budget` (`budget_id`, `section`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目预算明细行';

CREATE TABLE IF NOT EXISTS `project_budget_fee_standard` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `fee_key` VARCHAR(50) NOT NULL COMMENT '稳定键，预算明细 item_key 引用',
  `name` VARCHAR(200) NOT NULL,
  `scene` VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '适用大类：ONLINE 线上 / OFFLINE 线下',
  `default_value` VARCHAR(200) NULL COMMENT '默认值；区间类存低,高（如 300,400）；NULL=视需求报价无默认',
  `unit` VARCHAR(50) NULL COMMENT '场/人天/张/个/元等',
  `remark` VARCHAR(500) NULL,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_pbfs_key` (`fee_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议预算收费标准默认值';

INSERT IGNORE INTO `project_budget_fee_standard` (`fee_key`,`name`,`default_value`,`unit`,`remark`) VALUES
('MC_INTERLUDE',    '串场',                '500',   '元/场',   '公司收取'),
('MC_LETTER',       '沟通函及延展',        '500',   '元/场',   '公司收取'),
('AGENDA_UP',       '日程上浮',            '5,10',  '张',      '参会人员基础上浮张数'),
('MAT_DESKCARD_UP', '台卡上浮',            '10,20', '张',      '参会人员基础上浮张数'),
('MAT_STAND',       '展架（易拉宝）',      '4',     '个/场',   '按每场 4 个易拉宝匹配'),
('MAT_BANNER',      '条幅',                NULL,    '',        '按酒店提供尺寸，默认写真布，视需求报价'),
('MAT_PRINT_OTHER', '其他打印物料',        '500',   '元',      '按 500 预估'),
('MAT_ROSTRUM_FLOWER','讲台花',            '300,400','元',     '按 300-400 预估'),
('MAT_FLOWER_OTHER','桌花及其他花束',      NULL,    '',        '视需求报价'),
('MAT_DESIGN',      '物料设计费',          NULL,    '',        '视需求报价'),
('GIFT_UNIT',       '礼品单价',            '200,300','元/份',  '和客户确认是否需要，按 200-300 预估'),
('STAFF_DAY',       '人员费',              '500',   '元/人/天','固定费用；交通/住宿据实报销'),
('STAFF_TRAVEL',    '人员交通/住宿',       NULL,    '',        '据实报销，不做预算预估');
