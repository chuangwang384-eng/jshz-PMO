-- =============================================================
-- 客户信息模块（原知识星球"客户喜好"改造为独立客户档案管理）
-- 幂等：可重复执行（CREATE TABLE IF NOT EXISTS / INSERT IGNORE / information_schema 判列）
-- 变更内容：
--   1) 新建 customer / customer_attachment / customer_relation / customer_relation_type 四张表
--   2) 预置关系类型（介绍关系/上下级/同事/业务伙伴/亲友）
--   3) 新增权限码 customer:manage（一码多路径），绑定 ADMIN 与 KNOWLEDGE_MAINTAINER
--   4) 历史数据处理：knowledge_document 表 category='customer-preferences' 分类下存量文档
--      先备份到 *_customer_backup_20261029 表，再删除（仅删该分类，不波及其他分类；
--      关联的收藏/版本一并备份删除；download_log 日志仅备份不删除，保留审计链）
-- 设计依据：docs/2026-10-27-客户信息模块需求与技术方案.md
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 客户档案主表 ----------------

CREATE TABLE IF NOT EXISTS `customer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '客户姓名',
  `work_unit` VARCHAR(255) DEFAULT NULL COMMENT '工作单位',
  `work_position` VARCHAR(255) DEFAULT NULL COMMENT '工作职位',
  `work_level` VARCHAR(100) DEFAULT NULL COMMENT '工作职级',
  `current_duty` VARCHAR(255) DEFAULT NULL COMMENT '现任职务',
  `preferences` TEXT COMMENT '客户喜好（文本）',
  `remark` TEXT COMMENT '备注',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0正常 1已删除（默认隐藏，可恢复）',
  `delete_by` VARCHAR(50) DEFAULT NULL COMMENT '删除人',
  `delete_time` DATETIME DEFAULT NULL COMMENT '删除时间',
  `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(50) DEFAULT NULL COMMENT '最近更新人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案表';

-- ---------------- 2. 客户档案附件表 ----------------

CREATE TABLE IF NOT EXISTS `customer_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT NOT NULL COMMENT '客户档案ID customer.id',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `storage_name` VARCHAR(500) NOT NULL COMMENT '存储文件名（客户附件库内）',
  `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件扩展名（小写）',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `upload_by` VARCHAR(50) DEFAULT NULL COMMENT '上传人',
  `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案附件表';

-- ---------------- 3. 客户关系类型表 ----------------

CREATE TABLE IF NOT EXISTS `customer_relation_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '类型编码（预置固定，自定义为 custom_<时间戳>）',
  `name` VARCHAR(100) NOT NULL COMMENT '类型名称',
  `builtin` TINYINT NOT NULL DEFAULT 0 COMMENT '1预置类型 0自定义类型',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用（可停用不可物理删除）',
  `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户关系类型表（预置枚举+维护人可扩展）';

INSERT IGNORE INTO `customer_relation_type` (`code`, `name`, `builtin`, `status`) VALUES
('introduction', '介绍关系', 1, 1),
('superior-subordinate', '上下级', 1, 1),
('colleague', '同事', 1, 1),
('business-partner', '业务伙伴', 1, 1),
('friend-relative', '亲友', 1, 1);

-- ---------------- 4. 客户关系表（有向） ----------------

CREATE TABLE IF NOT EXISTS `customer_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `from_customer_id` BIGINT NOT NULL COMMENT '关系起点客户ID customer.id',
  `to_customer_id` BIGINT NOT NULL COMMENT '关系终点客户ID customer.id',
  `relation_type_id` BIGINT NOT NULL COMMENT '关系类型ID customer_relation_type.id',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '关系说明',
  `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_from_to_type` (`from_customer_id`, `to_customer_id`, `relation_type_id`),
  KEY `idx_to_customer_id` (`to_customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户关系表（有向，如"介绍关系"）';

-- ---------------- 5. 权限码 customer:manage（一码多路径） ----------------
-- 仅管理接口需要；查看接口（GET 列表/详情/附件下载/关系图/关系类型列表）登录即可，不配置权限码。
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('customer:manage', '客户档案新增', 2, 'POST', '/api/customer'),
('customer:manage', '客户档案编辑', 2, 'PUT', '/api/customer/**'),
('customer:manage', '客户档案软删除', 2, 'DELETE', '/api/customer/**'),
('customer:manage', '客户档案恢复', 2, 'POST', '/api/customer/*/restore'),
('customer:manage', '客户附件上传', 2, 'POST', '/api/customer/*/attachments'),
('customer:manage', '客户附件删除', 2, 'DELETE', '/api/customer/attachments/**'),
('customer:manage', '客户关系建立', 2, 'POST', '/api/customer/relations'),
('customer:manage', '客户关系删除', 2, 'DELETE', '/api/customer/relations/**'),
('customer:manage', '客户关系类型配置', 2, 'POST', '/api/customer/relations/types'),
('customer:manage', '客户关系类型配置', 2, 'PUT', '/api/customer/relations/types/**');

-- 绑定 ADMIN 与 KNOWLEDGE_MAINTAINER（复用现有角色，不新建角色）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.code FROM `role` r, `permission` p
WHERE r.`code` IN ('ADMIN', 'KNOWLEDGE_MAINTAINER') AND p.`code` = 'customer:manage';

-- SUPER_ADMIN 代码内强制全权，无需绑定

-- ---------------- 6. 历史数据处理：customer-preferences 分类存量文档 备份 + 删除 ----------------
-- 仅删该分类，不波及其他分类；客户信息模块不再沿用知识文档的收藏、下载计数、版本机制。

CREATE TABLE IF NOT EXISTS `knowledge_document_customer_backup_20261029` LIKE `knowledge_document`;
INSERT IGNORE INTO `knowledge_document_customer_backup_20261029`
SELECT d.* FROM `knowledge_document` d WHERE d.`category` = 'customer-preferences';

CREATE TABLE IF NOT EXISTS `knowledge_doc_version_customer_backup_20261029` LIKE `knowledge_doc_version`;
INSERT IGNORE INTO `knowledge_doc_version_customer_backup_20261029`
SELECT v.* FROM `knowledge_doc_version` v
JOIN `knowledge_document` d ON v.`doc_id` = d.`id`
WHERE d.`category` = 'customer-preferences';

CREATE TABLE IF NOT EXISTS `knowledge_favorite_customer_backup_20261029` LIKE `knowledge_favorite`;
INSERT IGNORE INTO `knowledge_favorite_customer_backup_20261029`
SELECT f.* FROM `knowledge_favorite` f
JOIN `knowledge_document` d ON f.`doc_id` = d.`id`
WHERE d.`category` = 'customer-preferences';

CREATE TABLE IF NOT EXISTS `download_log_customer_backup_20261029` LIKE `download_log`;
INSERT IGNORE INTO `download_log_customer_backup_20261029`
SELECT l.* FROM `download_log` l
JOIN `knowledge_document` d ON l.`doc_id` = d.`id`
WHERE d.`category` = 'customer-preferences' AND l.`download_type` = 'knowledge_doc';

DELETE FROM `knowledge_favorite` WHERE `doc_id` IN (SELECT id FROM `knowledge_document` WHERE `category` = 'customer-preferences');
DELETE FROM `knowledge_doc_version` WHERE `doc_id` IN (SELECT id FROM `knowledge_document` WHERE `category` = 'customer-preferences');
DELETE FROM `knowledge_document` WHERE `category` = 'customer-preferences';

-- ---------------- 7. 校验输出 ----------------
SELECT 'customer' AS table_name, COUNT(*) AS row_count FROM `customer`
UNION ALL SELECT 'customer_attachment', COUNT(*) FROM `customer_attachment`
UNION ALL SELECT 'customer_relation', COUNT(*) FROM `customer_relation`
UNION ALL SELECT 'customer_relation_type', COUNT(*) FROM `customer_relation_type`
UNION ALL SELECT 'permission(customer:manage)', COUNT(*) FROM `permission` WHERE `code` = 'customer:manage'
UNION ALL SELECT 'role_permission(customer:manage)', COUNT(*) FROM `role_permission` WHERE `permission_code` = 'customer:manage'
UNION ALL SELECT 'knowledge_document(剩余customer-preferences)', COUNT(*) FROM `knowledge_document` WHERE `category` = 'customer-preferences'
UNION ALL SELECT 'customer-preferences备份数', COUNT(*) FROM `knowledge_document_customer_backup_20261029`;
