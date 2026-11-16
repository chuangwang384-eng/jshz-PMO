-- =============================================================
-- 存储管理与硬删除工具：权限码 storage:manage（Owner 2026-11-12）
-- 幂等：INSERT IGNORE；UTF-8 执行必须走字节流重定向
-- 范围：菜单 + 5 条接口路径；绑定 ADMIN 角色；SUPER_ADMIN 代码内强制全权无需绑定
-- 设计文档：docs/design/2026-11-12-存储管理与硬删除工具-设计文档.md
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 菜单权限 ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('storage:manage', '存储管理', 1, 'MENU', 'storage:manage');

-- ---------------- 2. 接口权限（一码多路径） ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('storage:manage', '存储管理-统计预警', 2, 'GET', '/api/storage-admin/stats'),
('storage:manage', '存储管理-孤儿扫描', 2, 'GET', '/api/storage-admin/project-orphans'),
('storage:manage', '存储管理-孤儿文件清理', 2, 'POST', '/api/storage-admin/project-orphans/purge'),
('storage:manage', '存储管理-预览缓存清理', 2, 'POST', '/api/storage-admin/preview-cache/purge'),
('storage:manage', '存储管理-项目硬删预览', 2, 'GET', '/api/storage-admin/projects/*/purge-preview'),
('storage:manage', '存储管理-项目硬删除', 2, 'POST', '/api/storage-admin/projects/*/purge');

-- ---------------- 3. 绑定 ADMIN 角色（按 role.code 查询，禁止硬编码 id） ----------------
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'storage:manage' FROM `role` r WHERE r.`code` = 'ADMIN';

-- ---------------- 4. 回滚参考（非执行语句，仅文档） ----------------
-- DELETE FROM `role_permission` WHERE `permission_code` = 'storage:manage';
-- DELETE FROM `permission` WHERE `code` = 'storage:manage';
