-- REVIEW-FIX-20261106 / P2-7; MySQL 8+ additive, repeatable.
-- Purpose: record asynchronous knowledge-document extraction/compliance parsing status.
-- Apply only after backup. No data deletion; old code ignores these columns.

SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `parse_status` VARCHAR(16) NULL COMMENT ''解析状态：PENDING/RUNNING/SUCCESS/FAILED；NULL=历史数据/不适用''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'parse_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `parse_message` VARCHAR(500) NULL COMMENT ''解析结果说明（成功摘要或失败原因）''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'parse_message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `parse_time` DATETIME NULL COMMENT ''最近一次解析完成时间''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'parse_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
