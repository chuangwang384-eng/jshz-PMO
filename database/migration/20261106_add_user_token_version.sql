-- REVIEW-FIX-20261106 / P1-2; MySQL 8+ additive, repeatable.
-- Purpose: invalidate already-issued JWTs after account disable/delete/password/role changes.
-- Apply only after backup. No data deletion; rollback may keep this column (old code ignores it).

SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''Token 版本号：停用/删除/改密/改角色时自增，旧 Token 立即失效''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'token_version'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
