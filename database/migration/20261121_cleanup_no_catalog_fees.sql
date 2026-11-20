-- MySQL 8.0。清理无类目收费项（2026-11-21，Owner 授权「删除所有没有类目的收费项，已关联项目直接取消／连同明细行一起删除」）。
-- 1) 删除引用无类目收费项的预算明细行；2) 删除无类目收费项本身；
-- 3) 重算各预算单合计（剩余明细）；4) 已确认预算同步项目财务登记金额。
-- 可重跑：删除按 catalog IS NULL 匹配（删后为空，幂等）；重算为幂等覆盖。
START TRANSACTION;

DELETE i FROM project_budget_item i
  JOIN project_budget_fee_standard f ON i.item_key = f.fee_key
  WHERE f.catalog IS NULL;

DELETE FROM project_budget_fee_standard WHERE catalog IS NULL;

UPDATE project_budget b SET
  total_amount = COALESCE((SELECT SUM(i.amount) FROM project_budget_item i WHERE i.budget_id = b.id), 0),
  cost_total   = COALESCE((SELECT SUM(i.cost_amount) FROM project_budget_item i WHERE i.budget_id = b.id), 0);

UPDATE project p JOIN project_budget b ON b.project_id = p.id AND b.status = 'CONFIRMED'
  SET p.budget_amount = b.total_amount;

COMMIT;
