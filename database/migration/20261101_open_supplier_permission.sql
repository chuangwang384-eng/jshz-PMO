-- =============================================================
-- 2026-11-01 供应商管理权限放开（1.0.16 蓝绿发布）
-- 目标：
--   1) 新增权限码 supplier:create（POST /api/supplier）：全部非超管角色可新增供应商；
--   2) 新增权限码 supplier:binding（POST /api/supplier/*/bindings + DELETE /api/supplier/*/bindings/*）：
--      全部非超管角色可维护酒店-打印店绑定/解绑；
--   3) 补充 supplier:manage 缺失规则（PUT /api/supplier/*/images/*/name 图片改名、
--      POST /api/supplier/bindings/unbind-batch 跨城批量解绑），仅 ADMIN 可见；
--   4) supplier:dict 补绑 ADMIN（类型管理维持仅管理员）。
-- 关键设计（同 20261028 先例）：
--   DbPermissionFilter 遍历规则命中第一条即校验权限码；permission 规则按 id 升序。
--   若简单 INSERT 新 create/binding 行，旧 supplier:manage 行（id 188/196/197）会先命中，
--   导致非超管角色 403，放开失败。因此采用「规则改造」：将 manage 的同路径行
--   DELETE/UPDATE 转为 create/binding，保证 method+path 唯一。
-- 幂等：DELETE-if-new-exists + UPDATE-if-new-missing + INSERT IGNORE，可重复执行。
-- =============================================================
USE `expert_system`;

-- ---------- 1. 规则改造：POST /api/supplier  manage -> supplier:create ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:create' AND `method` = 'POST' AND `path` = '/api/supplier' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:create', `name` = '供应商-新增'
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:create' AND `method` = 'POST' AND `path` = '/api/supplier' LIMIT 1) AS p2);

-- ---------- 2. 规则改造：POST /api/supplier/*/bindings  manage -> supplier:binding ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier/*/bindings'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:binding' AND `method` = 'POST' AND `path` = '/api/supplier/*/bindings' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:binding', `name` = '供应商-绑定'
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier/*/bindings'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:binding' AND `method` = 'POST' AND `path` = '/api/supplier/*/bindings' LIMIT 1) AS p2);

-- ---------- 3. 规则改造：DELETE /api/supplier/*/bindings/*  manage -> supplier:binding ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'DELETE' AND `path` = '/api/supplier/*/bindings/*'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:binding' AND `method` = 'DELETE' AND `path` = '/api/supplier/*/bindings/*' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:binding', `name` = '供应商-解绑'
WHERE `code` = 'supplier:manage' AND `method` = 'DELETE' AND `path` = '/api/supplier/*/bindings/*'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:binding' AND `method` = 'DELETE' AND `path` = '/api/supplier/*/bindings/*' LIMIT 1) AS p2);

-- ---------- 4. 兜底 INSERT（含 supplier:create / supplier:binding 目标行，以及
--                supplier:manage 缺失的两条规则）---------- 
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('supplier:create', '供应商-新增', 2, 'POST', '/api/supplier'),
('supplier:binding', '供应商-绑定', 2, 'POST', '/api/supplier/*/bindings'),
('supplier:binding', '供应商-解绑', 2, 'DELETE', '/api/supplier/*/bindings/*'),
('supplier:manage', '供应商-图片改名', 2, 'PUT', '/api/supplier/*/images/*/name'),
('supplier:manage', '供应商-跨城批量解绑', 2, 'POST', '/api/supplier/bindings/unbind-batch');

-- ---------- 5. 角色绑定 ----------
-- 5.1 supplier:create 绑定全部非超管角色
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:create'
FROM `role` r
WHERE r.`code` <> 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'supplier:create' LIMIT 1) AS x);

-- 5.2 supplier:binding 绑定全部非超管角色
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:binding'
FROM `role` r
WHERE r.`code` <> 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'supplier:binding' LIMIT 1) AS x);

-- 5.3 supplier:dict 补绑 ADMIN（类型管理维持仅管理员）
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:dict'
FROM `role` r
WHERE r.`code` = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'supplier:dict' LIMIT 1) AS x);

-- 5.4 supplier:manage 补充规则仅 ADMIN 可见（幂等重绑，覆盖新增的图片改名/跨城批量解绑规则）
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:manage'
FROM `role` r
WHERE r.`code` = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'supplier:manage' LIMIT 1) AS x);
