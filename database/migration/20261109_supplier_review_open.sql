-- =============================================================
-- 20261109_supplier_review_open.sql
-- 供应商「服务评价」权限放开至全员（1.0.37.22 配套）
-- 目标（需求人 2026-11-09 确认）：任何登录用户都能在详情页「服务评价」里对供应商打分+写评价。
-- 现状：POST /api/supplier/*/reviews 绑在 supplier:manage（仅 ADMIN），员工/审批人/项目负责人
--       等角色既看不到「新增评价」按钮，直接调用也会 403。
-- 做法：
--   1) 新增权限码 supplier:review，覆盖 POST /api/supplier/*/reviews；
--   2) 「规则改造」而非新增行：DbPermissionFilter 按 id 升序命中第一条即校验，
--      若保留旧 supplier:manage 行，非 ADMIN 会先命中旧行继续 403；
--   3) supplier:review 绑定全部非 SUPER_ADMIN 角色（SUPER_ADMIN 代码内 '*' 全权，无需绑定）。
-- 生效条件：permission 规则与用户权限集均有进程内缓存，执行后需重启 ExpertSystemBackend
--           （或调用会触发 clearCaches 的角色保存接口）才生效。
-- 幂等：DELETE-if-new-exists + UPDATE-if-new-missing + INSERT IGNORE + 角色绑定 NOT EXISTS，可重复执行。
-- 回滚：
--   UPDATE `permission` SET `code`='supplier:manage', `name`='供应商-添加评价'
--    WHERE `code`='supplier:review' AND `method`='POST' AND `path`='/api/supplier/*/reviews';
--   DELETE FROM `role_permission` WHERE `permission_code`='supplier:review';
-- =============================================================
USE `expert_system`;

-- ---------- 1. 规则改造：POST /api/supplier/*/reviews  manage -> supplier:review ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier/*/reviews'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:review' AND `method` = 'POST' AND `path` = '/api/supplier/*/reviews' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:review', `name` = '供应商-服务评价'
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier/*/reviews'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:review' AND `method` = 'POST' AND `path` = '/api/supplier/*/reviews' LIMIT 1) AS p2);

-- ---------- 2. 兜底 INSERT（首次执行/规则行被误删时补齐） ----------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('supplier:review', '供应商-服务评价', 2, 'POST', '/api/supplier/*/reviews');

-- ---------- 3. 角色绑定：supplier:review 绑定全部非超管角色 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:review'
FROM `role` r
WHERE r.`code` <> 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.role_id = r.`id` AND rp.permission_code = 'supplier:review' LIMIT 1) AS x);

-- ---------- 4. 核对（人工执行） ----------
-- SELECT id, code, name, method, path FROM permission WHERE path = '/api/supplier/*/reviews';
-- SELECT r.code FROM role_permission rp JOIN role r ON r.id = rp.role_id
--  WHERE rp.permission_code = 'supplier:review' ORDER BY r.id;
