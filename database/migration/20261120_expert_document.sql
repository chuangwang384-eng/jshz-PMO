-- MySQL 8.0。专家资料文件表：医生证等资质资料上传（2026-11-20）。
-- 可重跑（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
-- 文件实体存于 {STORAGE_ROOT}\专家资料库，经 /api/files-doc/** 登录后静态访问；
-- 表中 file_name 保留原始文件名用于展示，stored_name 为物理文件名。
START TRANSACTION;

CREATE TABLE IF NOT EXISTS `expert_document` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `expert_id` BIGINT NOT NULL COMMENT '专家 id（expert.id）',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（展示用）',
  `stored_name` VARCHAR(255) NOT NULL COMMENT '物理存储文件名（expertdoc_<expertId>_<ts>.<ext>）',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '字节数',
  `created_by` BIGINT NULL COMMENT '上传人用户 id',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_expert_document_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家资料文件（医生证等资质资料）';

-- 上传/删除走既有 expert:edit 权限码；列表登录即可（与照片一致）。
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('expert:edit', '专家资料上传', 2, 'POST', '/api/experts/*/documents'),
('expert:edit', '专家资料删除', 2, 'DELETE', '/api/experts/*/documents/*');

COMMIT;
