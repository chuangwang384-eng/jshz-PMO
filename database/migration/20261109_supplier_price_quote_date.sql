-- =============================================================
-- 20261109_supplier_price_quote_date.sql
-- 供应商历史价格可比较化（1.0.37.21 配套，需求 2026-11-09）
-- 目的：给行价条目补充「报价日期」与「报价有效期至」，让同一服务项可跨时间/跨供应商比较，
--       并支持前端标出「已过期」报价。服务项(item_name)与计费单位(unit)为既有字段，无需新增。
-- 影响：仅新增两个可空列，旧代码与历史数据完全兼容（NULL = 未填写，前端回退显示创建时间）。
-- 幂等：information_schema 判存后再 ALTER，可重复执行。
-- 回滚：ALTER TABLE `supplier_price_item` DROP COLUMN `quote_date`, DROP COLUMN `valid_until`;（先备份）
-- =============================================================
USE `expert_system`;

SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `supplier_price_item` ADD COLUMN `quote_date` DATE NULL COMMENT ''报价日期（NULL=未填写，回退用创建时间）''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_price_item' AND COLUMN_NAME = 'quote_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `supplier_price_item` ADD COLUMN `valid_until` DATE NULL COMMENT ''报价有效期至（NULL=长期有效）''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_price_item' AND COLUMN_NAME = 'valid_until'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
