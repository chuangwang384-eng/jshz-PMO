-- =============================================================
-- 2026-10-28 项目管理权限变更：全员可新建项目 + 行级数据权限
-- 修复版：MySQL ERROR 1093 修复（子查询包派生表），幂等语义不变
-- =============================================================
USE `expert_system`;

-- 1) POST /api/project：project:manage -> project:create
-- 若已存在 project:create 的 POST /api/project 规则，删除旧的 project:manage 行
DELETE FROM `permission`
WHERE `code` = 'project:manage' AND `method` = 'POST' AND `path` = '/api/project'
  AND EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
              WHERE `code` = 'project:create' AND `method` = 'POST' AND `path` = '/api/project' LIMIT 1) AS p2);

-- 若不存在 project:create 规则，将旧 manage 行改造为 create
UPDATE `permission`
SET `code` = 'project:create', `name` = '项目创建（全员）'
WHERE `code` = 'project:manage' AND `method` = 'POST' AND `path` = '/api/project'
  AND NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission`
                  WHERE `code` = 'project:create' AND `method` = 'POST' AND `path` = '/api/project' LIMIT 1) AS p2);

-- 2) project:create 绑定所有非 SUPER_ADMIN 角色
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'project:create'
FROM `role` r
WHERE r.`code` <> 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'project:create' LIMIT 1) AS x);

-- 3) 新增标记权限码 project:view-all
INSERT INTO `permission` (`code`, `name`, `type`, `method`, `path`)
SELECT 'project:view-all', '项目全部可见（管理员豁免）', 1, 'MENU', 'project:view-all'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT `code` FROM `permission` WHERE `code` = 'project:view-all' LIMIT 1) AS p3);

-- 4) project:view-all 仅绑定 ADMIN
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'project:view-all'
FROM `role` r
WHERE r.`code` = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'project:view-all' LIMIT 1) AS x2);