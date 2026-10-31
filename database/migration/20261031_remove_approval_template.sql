-- =============================================================
-- 移除审批申请字段模板（R-B4 功能下线）：清理模板接口权限路径
-- 幂等：可重复执行（条件 DELETE，无副作用）
-- 约束：仅删除 /api/approval/templates 相关 4 条权限路径（approval:config 复用一码多路径）；
--       保留审批流配置权限本身（approval:config 其他路径不受影响）；
--       表结构保留（approval_template 表、expert_change_request.template_id 列，A 方案不落库不读写）
-- =============================================================
USE `expert_system`;

DELETE FROM `permission`
WHERE `code` = 'approval:config'
  AND (
    (`method` = 'GET'    AND `path` = '/api/approval/templates/all')
    OR (`method` = 'POST'   AND `path` = '/api/approval/templates')
    OR (`method` = 'PUT'    AND `path` = '/api/approval/templates/**')
    OR (`method` = 'DELETE' AND `path` = '/api/approval/templates/**')
  );
