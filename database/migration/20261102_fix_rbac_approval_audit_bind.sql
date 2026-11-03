-- =============================================================
-- 审批流配置板块改造（权限修复 + 新端点权限挂接）
-- 背景：
--   1) 线上 role_permission 缺 approval:audit 绑定（ADMIN 仅 config+manage，
--      APPROVER 仅 manage），导致审批列表/通过/驳回接口存在 403 风险；
--      设计文档 20261022_add_rbac_tables.sql 本意 ADMIN/APPROVER 均含
--      approval:audit，需修复绑定。
--   2) 本次新增端点需补 permission 行：
--      - GET  /api/approval/config/list            （多流程配置列表，approval:config）
--      - PUT  /api/approval/config/{flowKey}       （单流程保存，approval:config）
--      - GET/PUT /api/approval/config/{flowKey}/users（具体审批人管理，approval:config）
--      - POST /api/approval/requests/{id}/return   （退回修改，approval:audit）
--      POST /api/approval/requests/{id}/resubmit 与提交类接口一致不建规则行
--      （登录即可，Service 内校验申请人本人；与 my-applications 口径一致）。
-- 幂等：INSERT IGNORE，可重复执行；UTF-8 执行必须走字节流重定向。
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 新增接口权限行 ----------------

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('approval:config', '审批流配置-流程列表', 2, 'GET', '/api/approval/config/list'),
('approval:config', '审批流配置-具体审批人列表', 2, 'GET', '/api/approval/config/*/users'),
('approval:config', '审批流配置-具体审批人保存', 2, 'PUT', '/api/approval/config/*/users'),
('approval:audit', '审批-退回修改', 2, 'POST', '/api/approval/requests/*/return');

-- ---------------- 2. 角色权限绑定修复 ----------------
-- ADMIN / APPROVER 补 approval:audit（线上缺失，403 风险）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'approval:audit' FROM `role` r WHERE r.`code` IN ('ADMIN', 'APPROVER');

-- ADMIN 补 approval:config（线上若缺失一并修复）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'approval:config' FROM `role` r WHERE r.`code` = 'ADMIN';
