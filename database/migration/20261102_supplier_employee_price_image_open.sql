-- =============================================================
-- 20261102_supplier_employee_price_image_open.sql
-- 供应商图片上传/改名 + 历史价格维护 权限放开至全员（1.0.20 前端版本配套）
-- 目标（需求人 2026-11-02 确认）：
--   1) 新增权限码 supplier:image（POST /api/supplier/*/images 图片上传、
--      PUT /api/supplier/*/images/*/name 图片改名）：全部非超管角色可用；
--   2) 新增权限码 supplier:price（PUT /api/supplier/*/prices 历史价格全量替换）：全部非超管角色可用；
--   3) 其余写操作维持 supplier:manage 仅 ADMIN（编辑/删除供应商、设主图、删图、评价、批量解绑）。
-- 关键设计（同 20261101 先例）：
--   DbPermissionFilter 按 id 升序命中第一条即校验；必须「规则改造」（manage 行 UPDATE 转新码），
--   不能简单 INSERT 新行（否则旧 manage 行先命中导致非超管 403）。
-- 生效条件：permission 规则有进程内缓存，执行后需重启 ExpertSystemBackend 服务。
-- 幂等：DELETE-if-new-exists + UPDATE-if-new-missing + INSERT IGNORE + 角色绑定 NOT EXISTS，可重复执行。
-- 回滚：将 supplier:image/supplier:price 三行 UPDATE 回 supplier:manage，删除 role_permission 两码绑定。
-- =============================================================
USE `expert_system`;

-- ---------- 1. 规则改造：POST /api/supplier/*/images  manage -> supplier:image ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier/*/images'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:image' AND `method` = 'POST' AND `path` = '/api/supplier/*/images' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:image', `name` = '供应商-图片上传'
WHERE `code` = 'supplier:manage' AND `method` = 'POST' AND `path` = '/api/supplier/*/images'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:image' AND `method` = 'POST' AND `path` = '/api/supplier/*/images' LIMIT 1) AS p2);

-- ---------- 2. 规则改造：PUT /api/supplier/*/images/*/name  manage -> supplier:image ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'PUT' AND `path` = '/api/supplier/*/images/*/name'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:image' AND `method` = 'PUT' AND `path` = '/api/supplier/*/images/*/name' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:image', `name` = '供应商-图片改名'
WHERE `code` = 'supplier:manage' AND `method` = 'PUT' AND `path` = '/api/supplier/*/images/*/name'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:image' AND `method` = 'PUT' AND `path` = '/api/supplier/*/images/*/name' LIMIT 1) AS p2);

-- ---------- 3. 规则改造：PUT /api/supplier/*/prices  manage -> supplier:price ----------
DELETE FROM `permission`
WHERE `code` = 'supplier:manage' AND `method` = 'PUT' AND `path` = '/api/supplier/*/prices'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'supplier:price' AND `method` = 'PUT' AND `path` = '/api/supplier/*/prices' LIMIT 1) AS p2);

UPDATE `permission`
SET `code` = 'supplier:price', `name` = '供应商-历史价格维护'
WHERE `code` = 'supplier:manage' AND `method` = 'PUT' AND `path` = '/api/supplier/*/prices'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'supplier:price' AND `method` = 'PUT' AND `path` = '/api/supplier/*/prices' LIMIT 1) AS p2);

-- ---------- 4. 兜底 INSERT ----------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('supplier:image', '供应商-图片上传', 2, 'POST', '/api/supplier/*/images'),
('supplier:image', '供应商-图片改名', 2, 'PUT', '/api/supplier/*/images/*/name'),
('supplier:price', '供应商-历史价格维护', 2, 'PUT', '/api/supplier/*/prices');

-- ---------- 5. 角色绑定：两码绑定全部非超管角色 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:image'
FROM `role` r
WHERE r.`code` <> 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'supplier:image' LIMIT 1) AS x);

INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'supplier:price'
FROM `role` r
WHERE r.`code` <> 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'supplier:price' LIMIT 1) AS x);

-- ---------- 6. 核对（人工执行） ----------
-- SELECT id,code,method,path FROM permission WHERE path IN ('/api/supplier/*/images','/api/supplier/*/images/*/name','/api/supplier/*/prices');
-- SELECT r.code, rp.permission_code FROM role_permission rp JOIN role r ON r.id=rp.role_id WHERE rp.permission_code IN ('supplier:image','supplier:price') ORDER BY r.id;
