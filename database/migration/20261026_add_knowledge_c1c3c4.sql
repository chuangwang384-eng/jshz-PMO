-- =============================================================
-- 知识星球增强（C1 内容检索 / C3 收藏与更新通知 / C4 下载次数统计）
-- 幂等：可重复执行（information_schema 判列 / CREATE TABLE IF NOT EXISTS）
-- 变更：
--   C1: knowledge_document 加 content 列（上传/更新时抽取正文，检索范围扩展）
--   C3: 新表 knowledge_favorite（收藏），更新文档时通知收藏者
--   C4: download_log 加 doc_id 列（文档下载计数，download_type='knowledge_doc'）
-- 设计依据：docs/design/2026-10-26-知识星球C1C3C4增强-设计文档.md
-- =============================================================
USE `expert_system`;

-- ---------------- C1: knowledge_document 加正文列 ----------------

SET @col_content := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_document'
    AND COLUMN_NAME = 'content'
);
SET @ddl_content := IF(@col_content = 0,
  'ALTER TABLE `knowledge_document` ADD COLUMN `content` MEDIUMTEXT NULL COMMENT ''文档正文（上传时抽取，pdf/doc/docx，截断存储，用于内容检索）'' AFTER `file_size`',
  'SELECT ''content already exists'' AS info');
PREPARE stmt FROM @ddl_content;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- C3: 收藏表 ----------------

CREATE TABLE IF NOT EXISTS `knowledge_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_id` BIGINT NOT NULL COMMENT '文档ID knowledge_document.id',
  `user_id` BIGINT NOT NULL COMMENT '用户ID sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_user` (`doc_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档收藏表';

-- ---------------- C4: download_log 加文档列 ----------------

SET @col_doc := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'download_log'
    AND COLUMN_NAME = 'doc_id'
);
SET @ddl_doc := IF(@col_doc = 0,
  'ALTER TABLE `download_log` ADD COLUMN `doc_id` BIGINT NULL COMMENT ''知识文档ID（download_type=knowledge_doc 时有值）'' AFTER `expert_name`',
  'SELECT ''doc_id already exists'' AS info');
PREPARE stmt FROM @ddl_doc;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_doc := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'download_log'
    AND INDEX_NAME = 'idx_doc_id'
);
SET @ddl_idx := IF(@idx_doc = 0,
  'ALTER TABLE `download_log` ADD INDEX `idx_doc_id` (`doc_id`)',
  'SELECT ''idx_doc_id already exists'' AS info');
PREPARE stmt FROM @ddl_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
