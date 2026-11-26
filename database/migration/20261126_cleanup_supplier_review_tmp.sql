-- =============================================================
-- 20261126_cleanup_supplier_review_tmp.sql
-- 供应商类型死数据清理（P2-3，2026-11-26 审查修复；幂等，可重复执行）
-- 清理评价功能联调遗留类型 `_review_tmp`（biz_key=NULL、2026-11-01 创建、生产实测 0 引用）：
-- 仅当该类型无任何供应商引用时删除，否则保留并跳过（不级联、不误删）。
-- 回滚：INSERT INTO supplier_type (name,sort) VALUES ('_review_tmp',0);
-- =============================================================
USE `expert_system`;

SET @tid = (SELECT id FROM supplier_type WHERE name = '_review_tmp' LIMIT 1);
SET @refs = IFNULL((SELECT COUNT(*) FROM supplier WHERE type_id = @tid), 0);
SET @sql = IF(@tid IS NOT NULL AND @refs = 0,
  'DELETE FROM supplier_type WHERE name = ''_review_tmp''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
