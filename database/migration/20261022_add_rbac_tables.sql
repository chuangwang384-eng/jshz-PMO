-- =============================================================
-- RBAC 权限体系升级（阶段1）：角色 / 权限点 / 角色权限 / 用户角色 四表
-- 幂等：可重复执行；所有 INSERT 使用 IGNORE 防重
-- 设计依据：docs/superpowers/specs/2026-10-22-rbac-upgrade-design.md
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 建表 ----------------

CREATE TABLE IF NOT EXISTS `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `builtin` TINYINT NOT NULL DEFAULT 0 COMMENT '1=内置角色（禁删、禁改code；SUPER_ADMIN 锁定不可改）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '权限码（可重复：菜单/接口可共用同一码）',
  `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
  `type` TINYINT NOT NULL COMMENT '1=菜单 2=接口',
  `method` VARCHAR(10) DEFAULT NULL COMMENT '接口 HTTP 方法；菜单行为 MENU',
  `path` VARCHAR(200) DEFAULT NULL COMMENT '接口路径（Ant 模式）；菜单行为权限码',
  `parent_code` VARCHAR(50) DEFAULT NULL COMMENT '父菜单权限码（预留）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_path` (`method`,`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点表';

CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL COMMENT '角色ID(role.id)',
  `permission_code` VARCHAR(50) NOT NULL COMMENT '权限码（关联 permission.code）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

CREATE TABLE IF NOT EXISTS `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID(sys_user.id)',
  `role_id` BIGINT NOT NULL COMMENT '角色ID(role.id)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- ---------------- 2. 内置角色（4 个） ----------------

INSERT IGNORE INTO `role` (`code`, `name`, `description`, `status`, `builtin`) VALUES
('SUPER_ADMIN', '超级管理员', '系统最高权限，代码内强制全权，权限绑定锁定不可修改', 1, 1),
('ADMIN', '管理员', '专家管理、审批、用户管理、下载留痕等全部业务权限', 1, 1),
('APPROVER', '审批人', '审批流处理（approval:audit）', 1, 1),
('EMPLOYEE', '普通员工', '默认登录角色，无业务权限（可提交审批申请）', 1, 1);

-- ---------------- 3. 菜单权限（6 个） ----------------
-- 菜单行 method=MENU、path=权限码，保证 UNIQUE(method,path) 幂等

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('expert:manage', '专家管理', 1, 'MENU', 'expert:manage'),
('audit:manage', '下载留痕', 1, 'MENU', 'audit:manage'),
('approval:manage', '审批管理', 1, 'MENU', 'approval:manage'),
('approval:config', '审批流配置', 1, 'MENU', 'approval:config'),
('user:manage', '用户管理', 1, 'MENU', 'user:manage'),
('role:manage', '角色管理', 1, 'MENU', 'role:manage');

-- ---------------- 4. 接口权限（12 个权限码） ----------------
-- expert:export 含 4 条路径；approval:audit 含 approve/reject/GET列表 3 条路径

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('expert:add', '专家新增', 2, 'POST', '/api/experts'),
('expert:edit', '专家修改', 2, 'PUT', '/api/experts/**'),
('expert:delete', '专家删除', 2, 'DELETE', '/api/experts/**'),
('expert:import', '专家导入', 2, 'POST', '/api/experts/import'),
('expert:template', '导入模板', 2, 'GET', '/api/experts/template'),
('expert:export', '专家导出-全量CSV', 2, 'GET', '/api/experts/export'),
('expert:export', '专家导出-员工ZIP', 2, 'GET', '/api/experts/export/employee'),
('expert:export', '专家导出-兼容全量ZIP', 2, 'GET', '/api/export/full'),
('expert:export', '专家导出-全量ZIP', 2, 'GET', '/api/experts/export/full'),
('approval:audit', '审批处理-通过', 2, 'POST', '/api/approval/requests/*/approve'),
('approval:audit', '审批处理-驳回', 2, 'POST', '/api/approval/requests/*/reject'),
('approval:audit', '审批列表', 2, 'GET', '/api/approval/requests'),
('approval:config', '审批流配置-接口', 2, 'PUT', '/api/approval/config'),
('audit:view', '下载留痕查看', 2, 'GET', '/api/audit/**'),
('user:manage', '用户管理-列表', 2, 'GET', '/api/users/**'),
('user:manage', '用户管理-新增', 2, 'POST', '/api/users/**'),
('user:manage', '用户管理-修改/分配角色', 2, 'PUT', '/api/users/**'),
('user:manage', '用户管理-删除', 2, 'DELETE', '/api/users/**'),
('upload:manage', '照片上传', 2, 'POST', '/api/upload/**'),
('upload:manage', '照片资源', 2, 'GET', '/api/upload/**'),
('role:manage', '角色管理-列表/详情', 2, 'GET', '/api/roles/**'),
('role:manage', '角色管理-新增', 2, 'POST', '/api/roles/**'),
('role:manage', '角色管理-修改/绑定权限', 2, 'PUT', '/api/roles/**'),
('role:manage', '角色管理-删除', 2, 'DELETE', '/api/roles/**'),
('role:manage', '权限点列表', 2, 'GET', '/api/permissions/**');

-- ---------------- 5. 默认角色权限绑定 ----------------
-- ADMIN：14 个唯一权限码（菜单5 + 接口11，其中 approval:config / user:manage 菜单与接口共用同一码）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.code FROM `role` r, `permission` p
WHERE r.`code` = 'ADMIN' AND p.`code` IN (
  'expert:manage', 'audit:manage', 'approval:manage', 'approval:config', 'user:manage',
  'expert:add', 'expert:edit', 'expert:delete', 'expert:import', 'expert:template', 'expert:export',
  'approval:audit', 'audit:view', 'upload:manage'
);

-- APPROVER：审批相关 2 个权限码
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.code FROM `role` r, `permission` p
WHERE r.`code` = 'APPROVER' AND p.`code` IN ('approval:manage', 'approval:audit');

-- EMPLOYEE：默认无权限绑定（0 行）
-- SUPER_ADMIN：代码内强制全权，不写入 role_permission（防误改导致权限失效）

-- ---------------- 6. 默认用户角色绑定 ----------------

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `sys_user` u JOIN `role` r ON r.`code` = 'SUPER_ADMIN'
WHERE u.`username` = 'admin';

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `sys_user` u JOIN `role` r ON r.`code` = 'EMPLOYEE'
WHERE u.`username` = '张三';
