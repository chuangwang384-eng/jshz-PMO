-- =============================================================
-- 修复：项目导出接口越权（全项目扫描·阻塞级）
-- 背景：GET /api/project/export 无 type=2 规则，任何登录用户均可导出全部项目数据。
-- 修复：新增 project:manage 规则；project:manage 已在 20261027_add_project_module.sql
--       绑定 ADMIN + PROJECT_OWNER，无需重复挂接角色（SUPER_ADMIN 代码内自动全权）。
-- 幂等：INSERT IGNORE，可重复执行；UTF-8 执行必须走字节流重定向。
-- =============================================================
USE `expert_system`;

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('project:manage', '项目-导出xlsx', 2, 'GET', '/api/project/export');
