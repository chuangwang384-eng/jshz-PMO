-- =============================================================
-- 20261126_announcement_move_permission.sql
-- 公告置顶排序接口权限（需求四 2026-11-26；幂等，可重复执行）
-- POST /api/announcement/{id}/move 绑定 announcement:manage（与置顶同码），双层校验 Service 兜底。
-- 回滚：DELETE FROM permission WHERE method='POST' AND path='/api/announcement/*/move';
-- =============================================================
USE `expert_system`;
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('announcement:manage', '公告置顶排序', 2, 'POST', '/api/announcement/*/move');
