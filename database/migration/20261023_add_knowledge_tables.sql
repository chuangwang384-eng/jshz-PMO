-- =============================================================
-- 知识星球内容管理（知识文档库）：2 张表 + RBAC 接口权限
-- 幂等：可重复执行；所有 INSERT 使用 IGNORE 防重
-- 设计依据：docs/superpowers/specs/2026-10-23-knowledge-planet-design.md（如有）
-- 对应功能：会议执行 / 协会合规要求 / 企业合规要求 / 客户喜好 四分类文档
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 知识文档主表 ----------------

CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `category` VARCHAR(50) NOT NULL COMMENT '分类（meeting-execution/association-compliance/enterprise-compliance/customer-preferences）',
  `title` VARCHAR(200) NOT NULL COMMENT '文档标题',
  `version` INT NOT NULL DEFAULT 1 COMMENT '当前版本号（从1递增）',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT '当前文件原始文件名',
  `file_path` VARCHAR(500) DEFAULT NULL COMMENT '当前文件存储文件名（知识文档库内）',
  `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件扩展名（小写）',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `upload_by` VARCHAR(50) DEFAULT NULL COMMENT '首次上传人',
  `update_by` VARCHAR(50) DEFAULT NULL COMMENT '最近更新人',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_title` (`title`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档主表';

-- ---------------- 2. 知识文档版本历史表 ----------------

CREATE TABLE IF NOT EXISTS `knowledge_doc_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_id` BIGINT NOT NULL COMMENT '文档ID(knowledge_document.id)',
  `version` INT DEFAULT NULL COMMENT '版本号（1,2,3...）',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件原始文件名',
  `file_path` VARCHAR(500) DEFAULT NULL COMMENT '文件存储文件名（知识文档库内）',
  `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件扩展名（小写）',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `upload_by` VARCHAR(50) DEFAULT NULL COMMENT '该版本上传人',
  `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '该版本上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档版本历史表';

-- ---------------- 3. 接口权限：knowledge:manage（下架） ----------------
-- type=2 接口权限；DELETE /api/knowledge/docs/**（含 DELETE /docs/{id} 下架）
-- 上传/更新/列表/版本/文件下载均不设权限码（登录即可）

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('knowledge:manage', '知识文档下架', 2, 'DELETE', '/api/knowledge/docs/**');

-- ---------------- 4. 绑定到 ADMIN 角色 ----------------
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.code FROM `role` r, `permission` p
WHERE r.`code` = 'ADMIN' AND p.`code` IN ('knowledge:manage');

-- SUPER_ADMIN 代码内强制全权，无需绑定
