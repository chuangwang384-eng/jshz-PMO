-- =============================================================
-- 修复：供应商模块写接口越权（全项目扫描·阻塞级）
-- 背景：SupplierController 全部写接口（新增/编辑/删除/图片/行价/评价/绑定）
--       无 type=2 权限规则，任何登录用户均可操作（原设计为全员维护，扫描判定越权）。
-- 修复：新增权限码 supplier:manage（type=2）覆盖全部写接口，绑定 ADMIN
--       （SUPER_ADMIN 代码内自动全权，不写 role_permission）。
-- 幂等：INSERT IGNORE，可重复执行；UTF-8 执行必须走字节流重定向。
-- =============================================================
USE `expert_system`;

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('supplier:manage', '供应商-新增', 2, 'POST', '/api/supplier'),
('supplier:manage', '供应商-编辑', 2, 'PUT', '/api/supplier/*'),
('supplier:manage', '供应商-删除', 2, 'DELETE', '/api/supplier/*'),
('supplier:manage', '供应商-上传图片', 2, 'POST', '/api/supplier/*/images'),
('supplier:manage', '供应商-设为主图', 2, 'PUT', '/api/supplier/*/images/*/main'),
('supplier:manage', '供应商-删除图片', 2, 'DELETE', '/api/supplier/*/images/*'),
('supplier:manage', '供应商-保存行价', 2, 'PUT', '/api/supplier/*/prices'),
('supplier:manage', '供应商-添加评价', 2, 'POST', '/api/supplier/*/reviews'),
('supplier:manage', '供应商-绑定', 2, 'POST', '/api/supplier/*/bindings'),
('supplier:manage', '供应商-解绑', 2, 'DELETE', '/api/supplier/*/bindings/*');

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'supplier:manage' FROM `role` r WHERE r.`code` = 'ADMIN';
