-- =============================================================
-- 客户信息板块权限收敛：关系类型列表接口收敛为仅 customer:manage
-- 幂等：可重复执行（permission 表 uk_method_path 唯一键 + INSERT IGNORE）
-- 变更内容：
--   1) permission 表补充 GET /api/customer/relations/types 的 type=2 接口规则
--      （code=customer:manage），使后端权限过滤器对"仅登录"的该接口兜底拦截
--   2) 不动其它规则；ADMIN / KNOWLEDGE_MAINTAINER 已于 20261029 绑定 customer:manage，
--      无需重复绑定（SUPER_ADMIN 代码内强制全权）
-- 设计依据：docs/2026-11-02-客户信息板块权限收敛需求文档.md / 设计文档.md
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 关系类型列表接口权限收敛 ----------------
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('customer:manage', '客户关系类型列表', 2, 'GET', '/api/customer/relations/types');

-- ---------------- 2. 幂等校验（可重复执行） ----------------
SELECT COUNT(*) AS relation_types_get_rule_count FROM `permission`
WHERE `code` = 'customer:manage' AND `type` = 2
  AND `method` = 'GET' AND `path` = '/api/customer/relations/types';
