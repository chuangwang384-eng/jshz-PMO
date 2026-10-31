-- =============================================================
-- 修复：审批转办/加签接口越权（全项目扫描·阻塞级）
-- 背景：POST /api/approval/requests/{id}/transfer 与 add-approver 无 type=2
--       规则，任何登录用户均可调用（Service 仅校验 current_assignee）。
-- 修复：为两接口新增 approval:audit 规则（与既有 approve/reject 规则写法一致）；
--       approval:audit 已在 20261022_add_rbac_tables.sql 绑定 ADMIN + APPROVER，
--       无需重复挂接角色（SUPER_ADMIN 代码内自动全权）。
-- 幂等：INSERT IGNORE，可重复执行；UTF-8 执行必须走字节流重定向。
-- =============================================================
USE `expert_system`;

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('approval:audit', '审批-转办', 2, 'POST', '/api/approval/requests/*/transfer'),
('approval:audit', '审批-加签', 2, 'POST', '/api/approval/requests/*/add-approver');
