-- MySQL 8+. 组织树完善：部门负责人、总经理根节点、按公司简介重建三层组织、GENERAL_MANAGER 角色。
-- 可重复执行：列已存在则跳过；部门按 seed_key INSERT IGNORE；父级仅在仍为顶级时回填，保留管理员已有调整。
-- 前置：20261124_profile_manual_organization.sql（profile_department 已存在）。

SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'profile_department' AND column_name = 'leader_user_id');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE profile_department ADD COLUMN leader_user_id bigint DEFAULT NULL COMMENT ''部门负责人（sys_user.id）；根节点负责人即总经理'',
    ADD KEY idx_profile_department_leader (leader_user_id),
    ADD CONSTRAINT fk_profile_department_leader FOREIGN KEY (leader_user_id) REFERENCES sys_user(id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 节点：总经理（根）→ 总经办 → 4 个部门 → 12 个下级。HR_ADMIN 保留原 seed_key，仅改显示名称。
INSERT IGNORE INTO profile_department (seed_key, name) VALUES
('GENERAL_MANAGER', '总经理'),
('PROJECT_1', '项目一部'), ('PROJECT_2', '项目二部'), ('PROJECT_3', '项目三部'), ('KEY_PROJECT', '重点项目部'),
('ADMIN_AFFAIRS', '行政'), ('HUMAN_RESOURCES', '人力资源'), ('LEGAL', '法务'),
('PLANNING', '策划'), ('GRAPHIC_DESIGN', '平面设计'), ('DESIGN_3D', '3D设计'), ('NEW_MEDIA', '新媒体运营'),
('COMPLIANCE', '合规'), ('ACCOUNTING', '会计'), ('CASHIER', '出纳');

UPDATE profile_department SET name = '综合部' WHERE seed_key = 'HR_ADMIN';

-- 父级回填（仅顶级节点）
UPDATE profile_department c JOIN profile_department p ON p.seed_key = 'GENERAL_MANAGER'
  SET c.parent_id = p.id WHERE c.seed_key = 'GENERAL_OFFICE' AND c.parent_id IS NULL;

UPDATE profile_department c JOIN profile_department p ON p.seed_key = 'GENERAL_OFFICE'
  SET c.parent_id = p.id
  WHERE c.seed_key IN ('PROJECT', 'DESIGN', 'FINANCE_COMPLIANCE', 'HR_ADMIN') AND c.parent_id IS NULL;

UPDATE profile_department c JOIN profile_department p ON p.seed_key = 'PROJECT'
  SET c.parent_id = p.id
  WHERE c.seed_key IN ('PROJECT_1', 'PROJECT_2', 'PROJECT_3', 'KEY_PROJECT') AND c.parent_id IS NULL;

UPDATE profile_department c JOIN profile_department p ON p.seed_key = 'HR_ADMIN'
  SET c.parent_id = p.id
  WHERE c.seed_key IN ('ADMIN_AFFAIRS', 'HUMAN_RESOURCES', 'LEGAL') AND c.parent_id IS NULL;

UPDATE profile_department c JOIN profile_department p ON p.seed_key = 'DESIGN'
  SET c.parent_id = p.id
  WHERE c.seed_key IN ('PLANNING', 'GRAPHIC_DESIGN', 'DESIGN_3D', 'NEW_MEDIA') AND c.parent_id IS NULL;

UPDATE profile_department c JOIN profile_department p ON p.seed_key = 'FINANCE_COMPLIANCE'
  SET c.parent_id = p.id
  WHERE c.seed_key IN ('COMPLIANCE', 'ACCOUNTING', 'CASHIER') AND c.parent_id IS NULL;

-- 内置角色：权限由代码计算（全部权限码减去超管专属码），不写 role_permission。
-- 绑定只能通过组织树任命总经理完成，user_role 不在此处种子。
INSERT IGNORE INTO `role` (`code`, `name`, `description`, `status`, `builtin`, `create_time`, `update_time`) VALUES
('GENERAL_MANAGER', '总经理', '组织树根节点负责人，全系统唯一；拥有除角色管理、操作日志、企微同步、存储管理外的全部权限；仅可通过组织架构任命', 1, 1, NOW(), NOW());
