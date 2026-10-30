-- ============================================================
-- 供应商模块改造 v2.0「三、图片与主图」
-- 1) 新建 supplier_image 表（图片登记表）
--    说明：部分唯一索引 (supplier_id, is_main) WHERE is_main=1 语义
--    在 MySQL 中通过「生成列 main_key = IF(is_main=1, supplier_id, NULL)
--    + 唯一索引 uk_supplier_main(supplier_id, main_key)」等效实现
--    （is_main=0 时 main_key=NULL，NULL 不参与唯一约束，可多条）。
--    该列与索引由迁移脚本 migrate_supplier_image.py 在完成存量数据迁移与
--    主图选定后创建，避免迁移期间唯一约束冲突；本文件仅负责建表（幂等，可重复执行）。
-- 执行：mysql -uroot -p expert_system < 20261029_add_supplier_image.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `supplier_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `supplier_id` BIGINT NOT NULL COMMENT '供应商ID supplier.id',
  `file_name` VARCHAR(100) NOT NULL COMMENT '磁盘文件名（supplier_<uuid>.<ext>）',
  `name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '自定义显示名（≤50字符，空=显示原文件名）',
  `is_main` TINYINT NOT NULL DEFAULT 0 COMMENT '是否主图 1=是 0=否（每供应商至多一张主图）',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_file` (`supplier_id`, `file_name`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_is_main` (`supplier_id`, `is_main`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商图片表';
