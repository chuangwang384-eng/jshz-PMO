-- =============================================================
-- 20261126_announcement_top_sort.sql
-- 公告板块修复 + 置顶排序管理（1.0.155 配套；幂等，可重复执行）
-- 1) announcement 新列 top_sort：置顶组内排序值（值越小越靠前，仅 is_top=1 有意义）。
-- 2) 存量回填：对已置顶公告按 create_time DESC（既有展示顺序=新置顶在前）编号 1,2,3…。
--    回填守卫：仅当全部置顶行 top_sort=0（即从未人工排过序）才执行，
--    防止迁移重复执行/后续发布重放时覆盖管理员已调整的顺序。
-- 回滚：ALTER TABLE announcement DROP COLUMN top_sort;
--       （旧代码不感知该列，回滚不破坏数据；置顶回退为 create_time 排序）
-- =============================================================
USE `expert_system`;

-- ---------- 1. 加列（逐列 information_schema 守卫） ----------
SET @db = DATABASE();
SET @sql = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `announcement` ADD COLUMN `top_sort` int NOT NULL DEFAULT 0 COMMENT ''置顶组内排序值：值越小越靠前（仅 is_top=1 有意义）'' AFTER `is_top`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='announcement' AND COLUMN_NAME='top_sort');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. 存量回填（仅当全部置顶行仍未排序；按 create_time DESC 编号，保持既有展示顺序） ----------
SET @unsorted = (SELECT COUNT(*) FROM announcement WHERE is_top = 1 AND top_sort <> 0);
SET @tops = (SELECT COUNT(*) FROM announcement WHERE is_top = 1);
SET @sql = IF(@unsorted = 0 AND @tops > 0,
  'UPDATE announcement a JOIN (SELECT id, (@r := @r + 1) AS rn FROM announcement, (SELECT @r := 0) init WHERE is_top = 1 ORDER BY create_time DESC, id DESC) t ON a.id = t.id SET a.top_sort = t.rn',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
