-- =============================================================
-- 「我的申请」通用化改造：通用申请主表 application + 存量一次性迁移
-- 设计依据：docs/superpowers/specs/2026-10-26-my-applications-unified-design.md
-- 幂等：可重复执行（CREATE TABLE IF NOT EXISTS / INSERT IGNORE 保主键 / 判列后 ALTER / UPDATE 仅补空值）
-- 内容：
--   1) 新建 application 通用申请主表（含 B2 转办/加签两列 current_assignee / extra_approver，
--      保住 2026-10-26 上午刚上线的 B2 能力，规格未列但必须随表迁移）
--   2) expert_change_request 全量迁移（保留原 id，approval_record 映射=同 id）；
--      旧表数据保留不动作历史备份，新写入全部走 application
--   3) approval_record 加 application_id 列并按同 id 回填
--   4) 存量通知跳转路径 /admin/approval/manage → /profile/approval/manage（菜单迁移）
--   5) 无新增权限码：my-applications 登录即可；审批接口沿用既有路径规则
-- 状态语义沿用四态：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 通用申请主表 ----------------

CREATE TABLE IF NOT EXISTS `application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `applicant_id` BIGINT DEFAULT NULL COMMENT '申请人 sys_user.id（存量迁移按用户名回查，可空）',
  `applicant` VARCHAR(64) NOT NULL COMMENT '申请人用户名（兼容旧数据语义）',
  `application_type` VARCHAR(32) NOT NULL COMMENT '类型编码：EXPERT_MODIFY / EXPERT_CREATE（未来扩展 LEAVE 等）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回',
  `biz_data` JSON DEFAULT NULL COMMENT '业务详情 JSON：专家申请 {expertId, expertName, changes, reason, templateId}（照片 TMP: 前缀语义保留）',
  `approve_by` VARCHAR(64) DEFAULT NULL COMMENT '审批人用户名',
  `approve_comment` VARCHAR(500) DEFAULT NULL COMMENT '审批意见（驳回必填，通过可空）',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `current_assignee` VARCHAR(64) DEFAULT NULL COMMENT '当前审批人（B2 转办后有值；空=未转办，任何有审批权限的用户可审）',
  `extra_approver` VARCHAR(64) DEFAULT NULL COMMENT '加签审批人（B2 或签语义，任一审批即终态）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_applicant` (`applicant`),
  KEY `idx_applicant_id` (`applicant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用申请主表（我的申请，各业务线申请聚合）';

-- ---------------- 2. 存量迁移：expert_change_request -> application ----------------
-- 保留原 id（INSERT IGNORE 按主键幂等；发布窗口期旧表新写入可在部署后重跑本脚本补齐）
-- biz_data 组装：change_data 为 Jackson 写出的合法 JSON，JSON_VALID 兜底防脏数据中断迁移
-- applicant_id 按用户名回查 sys_user，查不到置空（applicant 必有值）；
-- 显式 COLLATE：sys_user 为 utf8mb4_general_ci，业务表为 utf8mb4_0900_ai_ci，避免混合排序规则报错
INSERT IGNORE INTO `application`
  (`id`, `applicant_id`, `applicant`, `application_type`, `status`, `biz_data`,
   `approve_by`, `approve_comment`, `approve_time`, `current_assignee`, `extra_approver`,
   `create_time`, `update_time`)
SELECT
  e.`id`,
  (SELECT u.`id` FROM `sys_user` u WHERE u.`username` COLLATE utf8mb4_general_ci = e.`applicant` COLLATE utf8mb4_general_ci LIMIT 1),
  e.`applicant`,
  CASE WHEN e.`request_type` = 1 THEN 'EXPERT_CREATE' ELSE 'EXPERT_MODIFY' END,
  IFNULL(e.`status`, 0),
  JSON_OBJECT(
    'expertId', e.`expert_id`,
    'expertName', e.`expert_name`,
    'changes', CASE WHEN e.`change_data` IS NOT NULL AND JSON_VALID(e.`change_data`)
                    THEN CAST(e.`change_data` AS JSON) ELSE JSON_OBJECT() END,
    'reason', e.`reason`,
    'templateId', e.`template_id`
  ),
  e.`approve_by`, e.`approve_comment`, e.`approve_time`,
  e.`current_assignee`, e.`extra_approver`,
  e.`create_time`,
  COALESCE(e.`update_time`, e.`create_time`)
FROM `expert_change_request` e;

-- ---------------- 3. approval_record 关联通用主表 ----------------

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'approval_record'
    AND COLUMN_NAME = 'application_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `approval_record` ADD COLUMN `application_id` BIGINT NULL COMMENT ''关联通用申请 application.id（新链路写入；存量按同 id 回填）'' AFTER `request_id`',
  'SELECT ''application_id already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填：application 与 expert_change_request 同 id，映射即恒等（仅补空值，幂等）
UPDATE `approval_record` SET `application_id` = `request_id`
WHERE `application_id` IS NULL AND `request_id` IS NOT NULL;

-- ---------------- 4. 存量通知跳转路径修正（审批管理菜单迁移至个人中心） ----------------

UPDATE `notification` SET `jump_path` = '/profile/approval/manage'
WHERE `jump_path` = '/admin/approval/manage';
