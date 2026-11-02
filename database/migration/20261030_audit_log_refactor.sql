-- =============================================================
-- 2026-10-30 用户操作日志板块改造：审计权限收紧为仅超管
-- 目标：
--   1) 移除 ADMIN 角色的 audit:view 绑定（权限码层面失去审计板块权限）；
--   2) SUPER_ADMIN 为代码内强制全权，不写 role_permission（与既有惯例一致）。
-- 幂等：DELETE 天然可重复执行；子查询仅引用 role 表，不触发 MySQL 同表删除限制。
-- 表结构说明：role_permission(role_id, permission_code)，无 deleted 字段；
--             role(code, status, builtin ...)，code 唯一（uk_code）。
-- =============================================================
USE `expert_system`;

DELETE FROM `role_permission`
WHERE `role_id` = (SELECT `id` FROM `role` WHERE `code` = 'ADMIN' LIMIT 1)
  AND `permission_code` = 'audit:view';
