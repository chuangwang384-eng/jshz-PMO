-- =============================================================
-- 存储管理：孤儿清理扩展到全目录（知识/照片/照片临时/客户/供应商）
-- Owner 2026-11-12 追加需求：各目录孤儿文件均可在管理页清理
-- 幂等：INSERT IGNORE；绑定 ADMIN；SUPER_ADMIN 代码内全权
-- 依赖：20261112_add_storage_manage_permission.sql（storage:manage 菜单与基础接口）
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 新增接口权限（全目录孤儿扫描/清理） ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('storage:manage', '存储管理-孤儿扫描(全目录)', 2, 'GET', '/api/storage-admin/orphans'),
('storage:manage', '存储管理-孤儿清理(全目录)', 2, 'POST', '/api/storage-admin/orphans/purge');

-- ---------------- 2. 绑定 ADMIN 角色（幂等） ----------------
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'storage:manage' FROM `role` r WHERE r.`code` = 'ADMIN';

-- ---------------- 3. 回滚参考（非执行语句） ----------------
-- DELETE FROM `permission` WHERE `code`='storage:manage' AND `path` LIKE '/api/storage-admin/orphans%';
