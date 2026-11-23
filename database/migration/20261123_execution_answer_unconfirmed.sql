-- MySQL 8.0。执行任务新增「复用答案待确认」标记（2026-11-23，1.0.134，需求19）。
-- 背景：「再次使用」复制上次答案时，复制过来的答案必须显式提示重新核对，避免被误认为本次已完成；
--       手工填写答案或显式确认后清除标记，不影响正常填写。
-- 可重跑：先查 information_schema，列已存在则跳过。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'compliance_execution_task' AND column_name = 'answer_unconfirmed'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE compliance_execution_task ADD COLUMN answer_unconfirmed TINYINT NOT NULL DEFAULT 0 COMMENT ''1=复用的答案待重新确认（手工填写或确认后置 0）'' AFTER answer',
  'SELECT ''answer_unconfirmed already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'compliance_execution_task' AND column_name = 'answer_unconfirmed_source'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE compliance_execution_task ADD COLUMN answer_unconfirmed_source VARCHAR(200) NULL COMMENT ''待确认来源说明（如 复用清单#12 的答案）'' AFTER answer_unconfirmed',
  'SELECT ''answer_unconfirmed_source already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
