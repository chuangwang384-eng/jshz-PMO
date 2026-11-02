-- =============================================================
-- 客户关系维护功能改造：关系类型方向 + 清空存量关系 + 索引
-- 幂等：可重复执行（information_schema 判列 / 判索引；DELETE 天然幂等）
-- 变更内容：
--   1) customer_relation_type 增加 direction 字段
--      （DIRECTED=单向有向 / BIDIRECTED=双向互见 / UNDIRECTED=无向）
--   2) 回填预置类型方向：
--      introduction(介绍关系)/superior-subordinate(上下级) = DIRECTED
--      colleague(同事)/friend-relative(亲友) = UNDIRECTED
--      business-partner(业务伙伴) = BIDIRECTED
--   3) 清空 customer_relation 存量数据（改造点：关系存储从纯有向改为按类型方向，
--      存量有向数据无法平滑转换，按设计文档要求清空重建）
--   4) customer_relation 增加 idx_from_customer_id 索引（配合 search-targets 排除已关联客户）
-- 执行前提：已在数据库执行 20261029_add_customer_module.sql
-- 风险提示：第 3 步为 DELETE 清空操作，不可恢复；执行前请自行确认已无需要保留的存量关系。
-- 设计依据：docs/客户关系维护功能设计文档.md
-- =============================================================
USE `expert_system`;

-- ---------------- 1. customer_relation_type 增加 direction 字段 ----------------
SET @has_direction := (
  SELECT COUNT(*) FROM information_schema.`COLUMNS`
  WHERE `TABLE_SCHEMA` = 'expert_system' AND `TABLE_NAME` = 'customer_relation_type' AND `COLUMN_NAME` = 'direction'
);
SET @ddl := IF(
  @has_direction = 0,
  'ALTER TABLE `customer_relation_type` ADD COLUMN `direction` VARCHAR(20) NOT NULL DEFAULT ''DIRECTED'' COMMENT ''关系方向：DIRECTED单向 / BIDIRECTED双向 / UNDIRECTED无向'' AFTER `status`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 2. 回填预置类型方向（仅回填 NULL/空值，自定义类型保持默认） ----------------
UPDATE `customer_relation_type` SET `direction` = 'DIRECTED'
WHERE `code` IN ('introduction', 'superior-subordinate') AND (`direction` IS NULL OR `direction` = '');

UPDATE `customer_relation_type` SET `direction` = 'UNDIRECTED'
WHERE `code` IN ('colleague', 'friend-relative') AND (`direction` IS NULL OR `direction` = '');

UPDATE `customer_relation_type` SET `direction` = 'BIDIRECTED'
WHERE `code` = 'business-partner' AND (`direction` IS NULL OR `direction` = '');

-- ---------------- 3. 清空 customer_relation 存量数据（幂等：重复执行无副作用） ----------------
DELETE FROM `customer_relation`;

-- ---------------- 4. customer_relation 增加 idx_from_customer_id 索引 ----------------
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.`STATISTICS`
  WHERE `TABLE_SCHEMA` = 'expert_system' AND `TABLE_NAME` = 'customer_relation' AND `INDEX_NAME` = 'idx_from_customer_id'
);
SET @ddl2 := IF(
  @has_idx = 0,
  'ALTER TABLE `customer_relation` ADD INDEX `idx_from_customer_id` (`from_customer_id`)',
  'SELECT 1'
);
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ---------------- 5. 校验输出 ----------------
SELECT `id`, `code`, `name`, `builtin`, `status`, `direction`
FROM `customer_relation_type` ORDER BY `id`;

SELECT 'customer_relation 剩余行数' AS `check`, COUNT(*) AS `value` FROM `customer_relation`;

SELECT COUNT(*) AS `idx_from_customer_id_exists`
FROM information_schema.`STATISTICS`
WHERE `TABLE_SCHEMA` = 'expert_system' AND `TABLE_NAME` = 'customer_relation' AND `INDEX_NAME` = 'idx_from_customer_id';
