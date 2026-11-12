-- =============================================================
-- 修复：approval_record 自增值与存量数据不同步（2026-11-11）
-- 现象：提交新增专家/修改申请时，写入审批进度记录报
--       Duplicate entry '138/139/140' for key 'approval_record.PRIMARY'。
-- 根因：表自增值被重置为 141，而存量最大主键为 151
--       （141~151 为 2026-11-07 的合法业务数据），新插入分配到已占用主键。
-- 修复：将 AUTO_INCREMENT 校正为 MAX(id)+1（仅元数据操作，不改动任何数据）。
-- 适用数据库：MySQL 8.x（expert_system）
-- 幂等：可重复执行。每次执行取 MAX(id)+1，重复执行只会得到同样的正确值。
-- 生产执行记录：2026-11-11 已执行，校正后自增从 152 开始分配，
--               事务内试插入分配到 152 且已 ROLLBACK（无残留数据）。
-- =============================================================
USE `expert_system`;

SET @next := (SELECT IFNULL(MAX(id), 0) + 1 FROM `approval_record`);
SET @ddl  := CONCAT('ALTER TABLE `approval_record` AUTO_INCREMENT = ', @next);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证（应返回 new_next >= MAX(id)+1，且无 138/139/140 冲突报错）
SELECT @next AS new_next;
SELECT MAX(id) AS max_id FROM `approval_record`;
