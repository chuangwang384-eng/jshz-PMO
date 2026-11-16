-- Isolated test fixture only. MySQL 8+, schema snapshot 2026-11-04.
-- No data, credentials, DROP statements or production migrations.
-- Execute ONLY after the test runner verifies pc_acceptance / 13317 / isolated datadir.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '公告标题',
  `content` mediumtext NOT NULL COMMENT '富文本正文（XSS 白名单清洗后入库）',
  `effective_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间（到达后才在首页/生效列表展示）',
  `expire_time` datetime DEFAULT NULL COMMENT '失效时间（空=永久有效）',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '置顶标记：0 否 / 1 是',
  `status` varchar(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '状态：ONLINE 上架 / OFFLINE 下架',
  `creator_id` bigint DEFAULT NULL COMMENT '发布人 sys_user.id',
  `creator_name` varchar(50) DEFAULT NULL COMMENT '发布人用户名快照',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_announce_status_time` (`status`,`effective_time`,`create_time`),
  KEY `idx_announce_top` (`is_top`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统公告主表';

CREATE TABLE IF NOT EXISTS `announcement_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `announcement_id` bigint NOT NULL COMMENT '公告 announcement.id',
  `user_id` bigint NOT NULL COMMENT '已读用户 sys_user.id',
  `read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`,`user_id`),
  KEY `idx_read_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告已读记录（唯一联合键保证幂等）';

CREATE TABLE IF NOT EXISTS `expert_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '姓名',
  `unit` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '单位',
  `department` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '科室',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职称',
  `research_direction` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '研究方向',
  `expertise_area` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '擅长领域',
  `contact` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系方式',
  `profile` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '简介',
  `id_card` varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证号（查看脱敏，前3后4）',
  `bank_card` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '银行卡号（查看脱敏，前3后4）',
  `photo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证件照文件名（相对照片库目录）',
  `bank_branch` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '银行卡开户行（R10 新增）',
  `id_card_front` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证正面图片文件名（R10 新增，仅管理页维护、下载包含）',
  `id_card_back` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证反面图片文件名（R10 新增，仅管理页维护、下载包含）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人id（登录用户名）',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_department` (`department`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='专家信息表（正式版 18 字段）';

CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '接收人 sys_user.id',
  `type` varchar(50) NOT NULL COMMENT '事件类型（approval_result/approval_todo/role_changed/password_reset/doc_off_shelf/doc_deleted）',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` varchar(500) DEFAULT NULL COMMENT '通知内容',
  `jump_path` varchar(255) DEFAULT NULL COMMENT '点击跳转前端路由（如 /profile/my-approvals?status=1）',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0 未读 / 1 已读',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_read` (`user_id`,`is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用消息通知表';

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '操作人 sys_user.id（可空，如历史迁移缺失）',
  `username` varchar(64) DEFAULT NULL COMMENT '操作人用户名',
  `action_type` varchar(32) NOT NULL COMMENT '操作类型枚举：DOWNLOAD/LOGIN/LOGOUT/CHANGE_PASSWORD/RESET_PASSWORD/CREATE_USER/UPDATE_USER/DELETE_USER/ASSIGN_ROLES/ROLE_PERMISSION/CREATE_EXPERT/UPDATE_EXPERT/DELETE_EXPERT/IMPORT_EXPERT/CREATE_DOC/UPDATE_DOC/OFF_SHELF_DOC/DELETE_DOC',
  `action_desc` varchar(500) DEFAULT NULL COMMENT '操作描述（含操作对象中文名）',
  `target_type` varchar(32) DEFAULT NULL COMMENT '对象类型：USER/ROLE/EXPERT/DOC 等',
  `target_id` varchar(64) DEFAULT NULL COMMENT '对象标识（id 或用户名）',
  `ip` varchar(64) DEFAULT NULL COMMENT '来源 IP',
  `source_id` bigint DEFAULT NULL COMMENT '来源记录ID（download_log.id，历史迁移标记；新写入为 NULL）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source` (`action_type`,`source_id`),
  KEY `idx_username` (`username`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户敏感操作日志表';

CREATE TABLE IF NOT EXISTS `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '权限码（可重复：菜单/接口可共用同一码）',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `type` tinyint NOT NULL COMMENT '1=菜单 2=接口',
  `method` varchar(10) DEFAULT NULL COMMENT '接口 HTTP 方法；菜单行为 MENU',
  `path` varchar(200) DEFAULT NULL COMMENT '接口路径（Ant 模式）；菜单行为权限码',
  `parent_code` varchar(50) DEFAULT NULL COMMENT '父菜单权限码（预留）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_path` (`method`,`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限点表';

CREATE TABLE IF NOT EXISTS `project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '项目名称（必填）',
  `category` varchar(20) NOT NULL COMMENT '大类：ONLINE 线上 / OFFLINE 线下（固定）',
  `type_id` bigint DEFAULT NULL COMMENT '类型 project_type.id',
  `start_date` date DEFAULT NULL COMMENT '计划开始时间',
  `end_date` date DEFAULT NULL COMMENT '计划结束时间',
  `owner_id` bigint DEFAULT NULL COMMENT '负责人 sys_user.id（限定拥有 PROJECT_OWNER 角色用户；立项必填）',
  `creator_id` bigint NOT NULL COMMENT '创建人 sys_user.id',
  `phase_id` bigint NOT NULL COMMENT '当前阶段 project_phase.id（项目状态）',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 正常 / CANCELED 已取消 / DELETED 已软删',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因（取消必填）',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `budget_amount` decimal(14,2) DEFAULT NULL COMMENT '预算金额（BUDGET 模板登记，项目级全局一份）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_phase` (`phase_id`),
  KEY `idx_project_owner` (`owner_id`),
  KEY `idx_project_type` (`type_id`),
  KEY `idx_project_category` (`category`),
  KEY `idx_project_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目主表（软删：status=DELETED 列表隐藏，数据保留）';

CREATE TABLE IF NOT EXISTS `project_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `expert_id` bigint NOT NULL COMMENT '专家 expert_info.id',
  `expert_name` varchar(50) NOT NULL COMMENT '专家姓名快照',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号快照（自动带出专家库，可改并回写 expert_info）',
  `bank_card` varchar(30) DEFAULT NULL COMMENT '银行卡号快照（自动带出专家库，可改并回写 expert_info）',
  `fee` decimal(14,2) DEFAULT NULL COMMENT '合同金额',
  `file_path` varchar(500) DEFAULT NULL COMMENT '合同文件路径（上传后登记）',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PENDING 审批中 / APPROVED 已生效 / REJECTED 已驳回',
  `approval_ref` bigint DEFAULT NULL COMMENT '审批单 id（复用 approval 模块 application.id）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '登记人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contract_project` (`project_id`),
  KEY `idx_contract_expert` (`expert_id`),
  KEY `idx_contract_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专家合同（登记信息+上传文件+发起审批；敏感字段回写 expert_info 并留痕）';

CREATE TABLE IF NOT EXISTS `project_doc` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `phase_id` bigint NOT NULL COMMENT '挂载阶段 project_phase.id（分阶段挂载）',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(500) NOT NULL COMMENT '文件路径（本地存储，同 expert 照片模式）',
  `upload_by` varchar(64) DEFAULT NULL COMMENT '上传人用户名',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删标记：1已删除',
  `deleted_by` varchar(64) DEFAULT NULL COMMENT '删除人用户名（留痕）',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间（留痕）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_project_phase` (`project_id`,`phase_id`),
  KEY `idx_doc_uploader` (`upload_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分阶段资料（上传无类型/大小限制；删除软删留痕）';

CREATE TABLE IF NOT EXISTS `project_executor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `user_id` bigint NOT NULL COMMENT '执行人 sys_user.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user` (`project_id`,`user_id`),
  KEY `idx_executor_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目执行人（多人；创建时默认带入创建人）';

CREATE TABLE IF NOT EXISTS `project_expert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `expert_id` bigint NOT NULL COMMENT '专家 expert_info.id',
  `role` varchar(100) DEFAULT NULL COMMENT '角色/分工（主持人/演讲嘉宾/评审专家等）',
  `fee` decimal(14,2) DEFAULT NULL COMMENT '劳务费（结算阶段登记）',
  `contract_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '合同状态快照：NONE/DRAFT/PENDING/APPROVED/REJECTED',
  `create_by` varchar(64) DEFAULT NULL COMMENT '关联人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_expert` (`project_id`,`expert_id`),
  KEY `idx_expert_rel_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目-专家关联（全局一份，不分阶段；移出留痕 operation_log）';

CREATE TABLE IF NOT EXISTS `project_phase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '阶段名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用（停用不再可选）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phase_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目流程阶段配置（可配置，项目当前阶段=状态）';

CREATE TABLE IF NOT EXISTS `project_phase_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目ID（project.id）',
  `from_phase_id` bigint NOT NULL COMMENT '原阶段（project_phase.id）',
  `to_phase_id` bigint NOT NULL COMMENT '目标阶段（project_phase.id）',
  `action_type` varchar(16) NOT NULL COMMENT '动作：ADVANCE 推进 / ROLLBACK 回退（含跨级）',
  `reason` varchar(500) DEFAULT NULL COMMENT '原因（回退必填，推进为空）',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_phase_hist_project` (`project_id`,`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目阶段变更历史（推进/回退含跨级）';

CREATE TABLE IF NOT EXISTS `project_phase_template_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phase_id` bigint NOT NULL COMMENT '阶段 project_phase.id',
  `template_id` bigint NOT NULL COMMENT '模板 project_template.id',
  `type_id` bigint NOT NULL DEFAULT '0' COMMENT '项目类型ID，0=全局兜底绑定',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填：1必填 0选填（推进校验用）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_phase_template` (`type_id`,`phase_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶段-模板绑定（管理员可配置，required 必填标记）';

CREATE TABLE IF NOT EXISTS `project_supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目 project.id',
  `supplier_id` bigint NOT NULL COMMENT '供应商 supplier.id',
  `purpose` varchar(100) DEFAULT NULL COMMENT '用途（按供应商类型自动带出，不手填）',
  `fee` decimal(14,2) DEFAULT NULL COMMENT '费用（过程中可填写）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '关联人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_supplier` (`project_id`,`supplier_id`),
  KEY `idx_supplier_rel_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目-供应商关联（全局一份；用途按类型自动带出；移出留痕）';

CREATE TABLE IF NOT EXISTS `project_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '模板名称',
  `template_type` varchar(20) NOT NULL COMMENT '模板类型枚举：FILE/EXPERT/SUPPLIER/BUDGET/RICH_TEXT',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人用户名（内置为 SYSTEM）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置项模板（内置 5 类 + 管理员可新建模板类型）';

CREATE TABLE IF NOT EXISTS `project_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '类型名称（管理员可增删改）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目类型字典';

CREATE TABLE IF NOT EXISTS `project_type_phase_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type_id` bigint NOT NULL COMMENT '项目类型ID（project_type.id）',
  `phase_id` bigint NOT NULL COMMENT '流程阶段ID（project_phase.id）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '阶段顺序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_phase` (`type_id`,`phase_id`),
  KEY `idx_type_id` (`type_id`),
  KEY `idx_phase_id` (`phase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目类型-流程阶段关联（类型自定义阶段序列）';

CREATE TABLE IF NOT EXISTS `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `builtin` tinyint NOT NULL DEFAULT '0' COMMENT '1=内置角色（禁删、禁改code；SUPER_ADMIN 锁定不可改）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID(role.id)',
  `permission_code` varchar(50) NOT NULL COMMENT '权限码（关联 permission.code）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';

CREATE TABLE IF NOT EXISTS `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '供应商名称',
  `type_id` bigint NOT NULL COMMENT '类型 supplier_type.id',
  `city_id` bigint NOT NULL COMMENT '城市 supplier_city.id（一供应商一城市）',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `contact` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(50) DEFAULT NULL COMMENT '电话',
  `wechat` varchar(50) DEFAULT NULL COMMENT '微信',
  `description` text COMMENT '描述',
  `images` text COMMENT '图片文件名 JSON 数组（前端压缩后上传，存储于供应商图库）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人用户名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_supplier_type` (`type_id`),
  KEY `idx_supplier_city` (`city_id`),
  KEY `idx_supplier_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商档案（全员创建维护，无审批）';

CREATE TABLE IF NOT EXISTS `supplier_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_key` varchar(20) DEFAULT NULL COMMENT '固定业务键：HOTEL/PRINT/MEDIA/IMAGING/FLEET/TICKET（内置类型锁定不改；自定义类型 NULL）',
  `name` varchar(50) NOT NULL COMMENT '类型名称（管理员可改）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小在前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`name`),
  UNIQUE KEY `uk_type_biz_key` (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商类型字典';

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录账号 = 员工姓名',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'BCrypt 加密密码（禁止明文）',
  `is_admin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0 普通员工 / 1 管理员（管理员名单）',
  `wecom_userid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '二期企微 userid（预留）',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号（唯一登录标识，未绑定用户为 NULL）',
  `position` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职务（企微 user/get position，同步展示用；非登录标识）',
  `must_change_password` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否需强制改密：1是 0否（设计文档 5.1 首次登录强制改密）',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1 启用 / 0 停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wecom_userid` (`wecom_userid`),
  UNIQUE KEY `uk_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID(sys_user.id)',
  `role_id` bigint NOT NULL COMMENT '角色ID(role.id)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表';
