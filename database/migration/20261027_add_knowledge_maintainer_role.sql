-- =============================================================
-- 知识星球维护人角色与权限（KNOWLEDGE_MAINTAINER / knowledge:maintain）
-- 幂等：可重复执行（INSERT IGNORE / DELETE 语句天然幂等）
-- 变更：
--   1) 新增接口权限码 knowledge:maintain（上传/更新/下架/永久删除/上架/版本历史）
--   2) 新增内置角色「知识星球维护人」KNOWLEDGE_MAINTAINER，绑定 knowledge:maintain
--   3) 移除旧权限码 knowledge:manage 及其 ADMIN 角色绑定（ADMIN 不再拥有知识星球管理权）
-- 设计依据：docs/2026-10-27-知识星球维护人角色与权限-design.md
-- 注意：执行后需重启后端（PermissionService 缓存 permission/role 表）
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 新增接口权限码 knowledge:maintain（一码多路径） ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('knowledge:maintain', '知识文档上传', 2, 'POST', '/api/knowledge/docs'),
('knowledge:maintain', '知识文档更新', 2, 'PUT', '/api/knowledge/docs/**'),
('knowledge:maintain', '知识文档下架与永久删除', 2, 'DELETE', '/api/knowledge/docs/**'),
('knowledge:maintain', '知识文档上架', 2, 'POST', '/api/knowledge/docs/*/relist'),
('knowledge:maintain', '知识文档版本历史', 2, 'GET', '/api/knowledge/docs/*/versions');

-- 注意：GET /api/knowledge/docs/*/versions 与 GET /api/knowledge/docs/files/** 不冲突
--       （versions 位于 /docs/{id}/versions，files 位于 /docs/files/**，路径段不同）

-- ---------------- 2. 新增内置角色 KNOWLEDGE_MAINTAINER ----------------
INSERT IGNORE INTO `role` (`code`, `name`, `description`, `status`, `builtin`) VALUES
('KNOWLEDGE_MAINTAINER', '知识星球维护人', '知识星球文档上传/更新/下架/上架/永久删除/版本历史/下载次数维护', 1, 1);

-- ---------------- 3. 绑定维护人角色 -> knowledge:maintain ----------------
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.code FROM `role` r, `permission` p
WHERE r.`code` = 'KNOWLEDGE_MAINTAINER' AND p.`code` = 'knowledge:maintain';

-- ---------------- 4. 移除旧 knowledge:manage 及 ADMIN 绑定 ----------------
DELETE FROM `role_permission` WHERE `permission_code` = 'knowledge:manage';
DELETE FROM `permission` WHERE `code` = 'knowledge:manage';

-- ---------------- 5. 校验输出 ----------------
SELECT 'permission:maintain' AS item, COUNT(*) AS cnt FROM `permission` WHERE `code` = 'knowledge:maintain';
SELECT 'permission:manage(应为0)' AS item, COUNT(*) AS cnt FROM `permission` WHERE `code` = 'knowledge:manage';
SELECT 'role:maintainer' AS item, COUNT(*) AS cnt FROM `role` WHERE `code` = 'KNOWLEDGE_MAINTAINER';
SELECT 'rp:maintainer-bind' AS item, COUNT(*) AS cnt FROM `role_permission` rp JOIN `role` r ON rp.role_id = r.id WHERE r.`code` = 'KNOWLEDGE_MAINTAINER' AND rp.permission_code = 'knowledge:maintain';
SELECT 'rp:admin-manage(应为0)' AS item, COUNT(*) AS cnt FROM `role_permission` rp JOIN `role` r ON rp.role_id = r.id WHERE r.`code` = 'ADMIN' AND rp.permission_code = 'knowledge:manage';
