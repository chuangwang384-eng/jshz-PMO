-- =============================================================
-- 20261126_budget_fee_permission.sql
-- 收费标准库（budget-fee-standards）权限收紧（2026-11-26；幂等，可重复执行）
-- 背景：GET/PUT/DELETE /api/project/config/budget-fee-standards* 此前无任何 permission 规则，
--       DbPermissionFilter 未命中即放行，登录用户即可改删收费标准；控制器注释也误称已绑定。
-- 变更：按精确路径新增 3 条 type=2 规则，统一绑定 project:manage（该码已通过
--       role_permission 授予管理角色 2/53，无需新增角色绑定）。
--       禁用 /api/project/config/** 通配（20261029 教训：会拦掉 EMPLOYEE 的
--       config/phases、config/types 字典读取，本迁移精确到 fee-standards 路径，不影响二者）。
-- 回滚：DELETE FROM permission WHERE type=2 AND code='project:manage'
--         AND path IN ('/api/project/config/budget-fee-standards',
--                      '/api/project/config/budget-fee-standards/*');
-- =============================================================
USE `expert_system`;
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('project:manage', '收费标准读取', 2, 'GET', '/api/project/config/budget-fee-standards'),
('project:manage', '收费标准保存', 2, 'PUT', '/api/project/config/budget-fee-standards'),
('project:manage', '收费标准删除', 2, 'DELETE', '/api/project/config/budget-fee-standards/*');

-- 验证（应返回 3 行；且 GET phases/types 的放开状态不受影响）：
-- SELECT method, path, code FROM permission
--  WHERE type=2 AND path LIKE '/api/project/config/budget-fee-standards%';
