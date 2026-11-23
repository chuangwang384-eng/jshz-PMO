-- MySQL 8.0。专家档案新增「身份证有效期」字段（2026-11-23，1.0.132）。
-- 上传身份证反面后由本机 PP-OCRv5 自动提取有效期限填入，允许手工修改。
-- 可重跑：先查 information_schema，列已存在则跳过。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'expert_info' AND column_name = 'id_card_expiry'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE expert_info ADD COLUMN id_card_expiry VARCHAR(50) NULL COMMENT ''身份证有效期（反面OCR自动提取，可手工修改，如 2015.05.01-2025.05.01 或 2015.05.01-长期）'' AFTER id_card_back',
  'SELECT ''id_card_expiry already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
