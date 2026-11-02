-- =============================================================
-- 项目管理模块：10 张新表 + 类型/阶段/模板种子 + 权限码 + 角色
-- 幂等：CREATE TABLE IF NOT EXISTS / INSERT IGNORE / INSERT...SELECT...WHERE NOT EXISTS；可重复执行
-- 设计要点：
--   1) 流程阶段完全可配置（project_phase），默认 6 阶段种子；项目当前阶段=状态（列表按状态筛选=按当前阶段）。
--   2) 配置项模板内置 5 类（FILE/EXPERT/SUPPLIER/BUDGET/RICH_TEXT），阶段-模板绑定可标必填/选填；
--      推进阶段时校验当前阶段必填模板内容已配置（见 ProjectService.advancePhase）。
--   3) 预算金额为项目级全局一份，扩展列 project.budget_amount（BUDGET 模板登记）；预算 vs 实际 =
--      预算金额 vs (专家劳务费Σ + 供应商费用Σ)。
--   4) 权限：项目列表/详情/时间线/导出等只读接口无 permission 规则（登录即可=project:view 全员默认）；
--      project:manage 保护创建/编辑/软删/关联/资料/合同/预算/config 管理，绑定 ADMIN + PROJECT_OWNER；
--      阶段推进/取消为项目级授权（负责人+执行人），不走权限码（Service 校验）。
--   5) 新增角色 PROJECT_OWNER（项目负责人，SUPER_ADMIN 自动拥有：admin 绑定 user_role）。
--   6) 项目/合同/资料均为软删或保留留痕；operation_log 的 action_type 为 Java 常量（无表结构变更）。
-- 执行注意：含中文，禁止经 PowerShell 管道导入，必须 --default-character-set=utf8mb4 重定向。
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 项目类型字典 ----------------
CREATE TABLE IF NOT EXISTS `project_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '类型名称（管理员可增删改）',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目类型字典';

INSERT IGNORE INTO `project_type` (`name`, `sort`) VALUES
('展会', 1),
('会议', 2),
('论坛', 3);

-- ---------------- 2. 流程阶段配置 ----------------
CREATE TABLE IF NOT EXISTS `project_phase` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '阶段名称',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用（停用不再可选）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phase_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目流程阶段配置（可配置，项目当前阶段=状态）';

INSERT IGNORE INTO `project_phase` (`name`, `sort`, `is_active`) VALUES
('立项', 1, 1),
('预算', 2, 1),
('筹备', 3, 1),
('进行中', 4, 1),
('结算', 5, 1),
('已完成', 6, 1);

-- ---------------- 3. 配置项模板 ----------------
CREATE TABLE IF NOT EXISTS `project_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '模板名称',
  `template_type` VARCHAR(20) NOT NULL COMMENT '模板类型枚举：FILE/EXPERT/SUPPLIER/BUDGET/RICH_TEXT',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人用户名（内置为 SYSTEM）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置项模板（内置 5 类 + 管理员可新建模板类型）';

INSERT IGNORE INTO `project_template` (`name`, `template_type`, `create_by`) VALUES
('资料附件', 'FILE', 'SYSTEM'),
('专家', 'EXPERT', 'SYSTEM'),
('供应商', 'SUPPLIER', 'SYSTEM'),
('预算', 'BUDGET', 'SYSTEM'),
('自定义文本', 'RICH_TEXT', 'SYSTEM');

-- ---------------- 4. 阶段-模板绑定 ----------------
CREATE TABLE IF NOT EXISTS `project_phase_template_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `phase_id` BIGINT NOT NULL COMMENT '阶段 project_phase.id',
  `template_id` BIGINT NOT NULL COMMENT '模板 project_template.id',
  `required` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：1必填 0选填（推进校验用）',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phase_template` (`phase_id`, `template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阶段-模板绑定（管理员可配置，required 必填标记）';

-- 默认绑定（全为选填或核心必填，保证默认推进不阻塞；管理员可调整）
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 1, 1 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'RICH_TEXT' WHERE ph.name = '立项';
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 1, 1 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'BUDGET' WHERE ph.name = '预算';
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 0, 1 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'FILE' WHERE ph.name = '筹备';
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 0, 2 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'EXPERT' WHERE ph.name = '筹备';
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 0, 3 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'SUPPLIER' WHERE ph.name = '筹备';
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 0, 1 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'FILE' WHERE ph.name = '进行中';
INSERT IGNORE INTO `project_phase_template_rel` (`phase_id`, `template_id`, `required`, `sort`)
SELECT ph.id, t.id, 0, 1 FROM `project_phase` ph JOIN `project_template` t ON t.template_type = 'FILE' WHERE ph.name = '结算';

-- ---------------- 5. 项目主表 ----------------
CREATE TABLE IF NOT EXISTS `project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '项目名称（必填）',
  `category` VARCHAR(20) NOT NULL COMMENT '大类：ONLINE 线上 / OFFLINE 线下（固定）',
  `type_id` BIGINT DEFAULT NULL COMMENT '类型 project_type.id',
  `start_date` DATE DEFAULT NULL COMMENT '计划开始时间',
  `end_date` DATE DEFAULT NULL COMMENT '计划结束时间',
  `owner_id` BIGINT DEFAULT NULL COMMENT '负责人 sys_user.id（限定拥有 PROJECT_OWNER 角色用户；立项必填）',
  `creator_id` BIGINT NOT NULL COMMENT '创建人 sys_user.id',
  `phase_id` BIGINT NOT NULL COMMENT '当前阶段 project_phase.id（项目状态）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 正常 / CANCELED 已取消 / DELETED 已软删',
  `cancel_reason` VARCHAR(500) DEFAULT NULL COMMENT '取消原因（取消必填）',
  `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
  `budget_amount` DECIMAL(14,2) DEFAULT NULL COMMENT '预算金额（BUDGET 模板登记，项目级全局一份）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_phase` (`phase_id`),
  KEY `idx_project_owner` (`owner_id`),
  KEY `idx_project_type` (`type_id`),
  KEY `idx_project_category` (`category`),
  KEY `idx_project_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目主表（软删：status=DELETED 列表隐藏，数据保留）';

-- ---------------- 6. 项目执行人（多人，默认创建人） ----------------
CREATE TABLE IF NOT EXISTS `project_executor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL COMMENT '项目 project.id',
  `user_id` BIGINT NOT NULL COMMENT '执行人 sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user` (`project_id`, `user_id`),
  KEY `idx_executor_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目执行人（多人；创建时默认带入创建人）';

-- ---------------- 7. 项目-专家关联 ----------------
CREATE TABLE IF NOT EXISTS `project_expert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL COMMENT '项目 project.id',
  `expert_id` BIGINT NOT NULL COMMENT '专家 expert_info.id',
  `role` VARCHAR(100) DEFAULT NULL COMMENT '角色/分工（主持人/演讲嘉宾/评审专家等）',
  `fee` DECIMAL(14,2) DEFAULT NULL COMMENT '劳务费（结算阶段登记）',
  `contract_status` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '合同状态快照：NONE/DRAFT/PENDING/APPROVED/REJECTED',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '关联人用户名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_expert` (`project_id`, `expert_id`),
  KEY `idx_expert_rel_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-专家关联（全局一份，不分阶段；移出留痕 operation_log）';

-- ---------------- 8. 项目-供应商关联 ----------------
CREATE TABLE IF NOT EXISTS `project_supplier` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL COMMENT '项目 project.id',
  `supplier_id` BIGINT NOT NULL COMMENT '供应商 supplier.id',
  `purpose` VARCHAR(100) DEFAULT NULL COMMENT '用途（按供应商类型自动带出，不手填）',
  `fee` DECIMAL(14,2) DEFAULT NULL COMMENT '费用（过程中可填写）',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '关联人用户名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_supplier` (`project_id`, `supplier_id`),
  KEY `idx_supplier_rel_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-供应商关联（全局一份；用途按类型自动带出；移出留痕）';

-- ---------------- 9. 分阶段资料 ----------------
CREATE TABLE IF NOT EXISTS `project_doc` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL COMMENT '项目 project.id',
  `phase_id` BIGINT NOT NULL COMMENT '挂载阶段 project_phase.id（分阶段挂载）',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `file_path` VARCHAR(500) NOT NULL COMMENT '文件路径（本地存储，同 expert 照片模式）',
  `upload_by` VARCHAR(64) DEFAULT NULL COMMENT '上传人用户名',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删标记：1已删除',
  `deleted_by` VARCHAR(64) DEFAULT NULL COMMENT '删除人用户名（留痕）',
  `deleted_time` DATETIME DEFAULT NULL COMMENT '删除时间（留痕）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_project_phase` (`project_id`, `phase_id`),
  KEY `idx_doc_uploader` (`upload_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分阶段资料（上传无类型/大小限制；删除软删留痕）';

-- ---------------- 10. 专家合同 ----------------
CREATE TABLE IF NOT EXISTS `project_contract` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL COMMENT '项目 project.id',
  `expert_id` BIGINT NOT NULL COMMENT '专家 expert_info.id',
  `expert_name` VARCHAR(50) NOT NULL COMMENT '专家姓名快照',
  `id_card` VARCHAR(18) DEFAULT NULL COMMENT '身份证号快照（自动带出专家库，可改并回写 expert_info）',
  `bank_card` VARCHAR(30) DEFAULT NULL COMMENT '银行卡号快照（自动带出专家库，可改并回写 expert_info）',
  `fee` DECIMAL(14,2) DEFAULT NULL COMMENT '合同金额',
  `file_path` VARCHAR(500) DEFAULT NULL COMMENT '合同文件路径（上传后登记）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PENDING 审批中 / APPROVED 已生效 / REJECTED 已驳回',
  `approval_ref` BIGINT DEFAULT NULL COMMENT '审批单 id（复用 approval 模块 application.id）',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '登记人用户名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contract_project` (`project_id`),
  KEY `idx_contract_expert` (`expert_id`),
  KEY `idx_contract_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家合同（登记信息+上传文件+发起审批；敏感字段回写 expert_info 并留痕）';

-- ---------------- 11. 角色：PROJECT_OWNER ----------------
INSERT IGNORE INTO `role` (`code`, `name`, `description`, `status`, `builtin`) VALUES
('PROJECT_OWNER', '项目负责人', '项目负责人：负责项目创建/推进/关联/结算等（绑 project:manage）', 1, 1);

-- ---------------- 12. 权限码：project:manage（接口规则，绑 ADMIN + PROJECT_OWNER） ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('project:manage', '项目创建', 2, 'POST', '/api/project'),
('project:manage', '项目编辑', 2, 'PUT', '/api/project/*'),
('project:manage', '项目软删', 2, 'DELETE', '/api/project/*'),
('project:manage', '专家关联', 2, 'POST', '/api/project/*/experts'),
('project:manage', '新建专家卡', 2, 'POST', '/api/project/*/experts/new-card'),
('project:manage', '专家移出', 2, 'DELETE', '/api/project/*/experts/*'),
('project:manage', '登记专家劳务费', 2, 'PUT', '/api/project/*/experts/*/fee'),
('project:manage', '供应商关联', 2, 'POST', '/api/project/*/suppliers'),
('project:manage', '供应商移出', 2, 'DELETE', '/api/project/*/suppliers/*'),
('project:manage', '资料上传', 2, 'POST', '/api/project/*/docs'),
('project:manage', '资料删除', 2, 'DELETE', '/api/project/*/docs/*'),
('project:manage', '保存自定义文本', 2, 'PUT', '/api/project/*/docs/richtext'),
('project:manage', '合同登记', 2, 'POST', '/api/project/*/contracts'),
('project:manage', '合同更新', 2, 'PUT', '/api/project/*/contracts/*'),
('project:manage', '合同敏感字段回写', 2, 'PUT', '/api/project/*/contracts/*/sensitive'),
('project:manage', '合同删除', 2, 'DELETE', '/api/project/*/contracts/*'),
('project:manage', '预算登记', 2, 'PUT', '/api/project/*/budget'),
('project:manage', '登记供应商费用', 2, 'PUT', '/api/project/*/suppliers/*/fee'),
('project:manage', '配置查看', 2, 'GET', '/api/project/config/**'),
('project:manage', '类型字典新增', 2, 'POST', '/api/project/config/types'),
('project:manage', '类型字典修改', 2, 'PUT', '/api/project/config/types/*'),
('project:manage', '类型字典删除', 2, 'DELETE', '/api/project/config/types/*'),
('project:manage', '阶段新增', 2, 'POST', '/api/project/config/phases'),
('project:manage', '阶段修改', 2, 'PUT', '/api/project/config/phases/*'),
('project:manage', '阶段删除', 2, 'DELETE', '/api/project/config/phases/*'),
('project:manage', '模板新增', 2, 'POST', '/api/project/config/templates'),
('project:manage', '模板修改', 2, 'PUT', '/api/project/config/templates/*'),
('project:manage', '模板删除', 2, 'DELETE', '/api/project/config/templates/*'),
('project:manage', '阶段模板绑定', 2, 'POST', '/api/project/config/rel'),
('project:manage', '阶段模板绑定删除', 2, 'DELETE', '/api/project/config/rel/*');

-- project:view：全员默认（登录即可），作为权限点登记（type=1 菜单行仅用于权限点展示，不参与接口过滤；
-- 只读接口无 type=2 规则 → DbPermissionFilter 未命中即放行；前端路由 meta.requiresAuth 控制登录可见）
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('project:view', '项目查看（全员默认）', 1, 'MENU', 'project:view');

-- 角色权限绑定：ADMIN + PROJECT_OWNER → project:manage（SUPER_ADMIN 代码内强制全权，不写 role_permission）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'project:manage' FROM `role` r WHERE r.`code` IN ('ADMIN', 'PROJECT_OWNER');

-- ---------------- 13. SUPER_ADMIN 自动获得 PROJECT_OWNER 角色 ----------------
-- admin 绑定 PROJECT_OWNER（幂等）；其它 SUPER_ADMIN 用户由 ProjectConfigController.owners() 代码层面并入
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `sys_user` u JOIN `role` r ON r.`code` = 'PROJECT_OWNER'
WHERE u.`username` = 'admin';
