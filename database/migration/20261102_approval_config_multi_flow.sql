-- =============================================================
-- 审批流配置板块改造（R1 多流程初始化）
-- 设计依据：docs/design/2026-10-31-审批流配置板块改造-设计文档.md（2.5 存量兼容）
-- 内容：
--   1) 备份 approval_config 全量（仅首次执行，后续重跑 IGNORE 不重复）
--   2) EXPERT_MODIFY → EXPERT 兼容迁移：保留原行数据，仅改流程键；
--      同时保持 approval_config_user 对旧流程键无依赖（兜底审批人按新键写入）
--   3) 新增 CONTRACT 流程行（INSERT IGNORE，幂等）
--   4) 兜底具体审批人：为新流程（EXPERT/CONTRACT）首次写入 APPROVER 角色下启用用户，
--      保证多流程提交时快照非空（仅当该流程尚无任何具体审批人时写入）
-- 幂等：条件 INSERT / INSERT IGNORE / 条件 DELETE，可重复执行
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 备份（幂等：全量重建，避免重复行） ----------------

DROP TABLE IF EXISTS `approval_config_bak_20261102`;
CREATE TABLE `approval_config_bak_20261102` LIKE `approval_config`;
INSERT INTO `approval_config_bak_20261102` SELECT * FROM `approval_config`;

-- ---------------- 2. EXPERT_MODIFY → EXPERT 兼容迁移 ----------------
-- 仅当旧键行存在且目标键行不存在时执行（INSERT 0 行则随后的条件 DELETE 也为 0 行）

INSERT INTO `approval_config` (`flow_key`, `flow_name`, `enabled`, `approver_role`)
SELECT 'EXPERT', `flow_name`, `enabled`, `approver_role`
FROM `approval_config`
WHERE `flow_key` = 'EXPERT_MODIFY'
  AND NOT EXISTS (SELECT 1 FROM `approval_config` c2 WHERE c2.`flow_key` = 'EXPERT');

DELETE FROM `approval_config`
WHERE `flow_key` = 'EXPERT_MODIFY'
  AND EXISTS (SELECT 1 FROM (SELECT `flow_key` FROM `approval_config` WHERE `flow_key` = 'EXPERT') AS c2);

-- ---------------- 3. CONTRACT 流程行（幂等） ----------------

INSERT IGNORE INTO `approval_config` (`flow_key`, `flow_name`, `enabled`, `approver_role`)
VALUES ('CONTRACT', '项目合同审批', 1, 'APPROVER');

-- ---------------- 4. 兜底具体审批人（EXPERT / CONTRACT） ----------------
-- 将 APPROVER 角色下启用用户写入具体审批人配置；仅当该流程尚无任何具体审批人时执行

INSERT IGNORE INTO `approval_config_user` (`config_id`, `user_id`, `username`)
SELECT c.`id`, u.`id`, u.`username`
FROM `approval_config` c
JOIN `user_role` ur ON ur.`role_id` = (SELECT r.`id` FROM `role` r WHERE r.`code` = 'APPROVER')
JOIN `sys_user` u ON u.`id` = ur.`user_id` AND u.`status` = 1
WHERE c.`flow_key` IN ('EXPERT', 'CONTRACT')
  AND NOT EXISTS (SELECT 1 FROM `approval_config_user` acu WHERE acu.`config_id` = c.`id`);
