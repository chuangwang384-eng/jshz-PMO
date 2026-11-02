-- =============================================================
-- 2026-11-01 公告板块合并：删除 permission 表 type=1 公告管理菜单行
-- 幂等：DELETE 可重复执行（第二次执行影响 0 行），可安全重放
-- 背景：
--   公告与公告管理合并为单页权限感知视图（AnnouncementList.vue 阅读/管理模式切换），
--   前端已删除 /profile/announcements/manage 路由与菜单，permission 表对应菜单行一并清理。
--   接口规则（type=2，method+path 供 DbPermissionFilter 匹配）必须完整保留。
-- 精确形态（与 20261028_announcement.sql 第 3 节 INSERT 一致）：
--   ('announcement:manage', '公告管理', 1, 'MENU', 'announcement:manage')
--   故删除条件：type=1 AND code='announcement:manage' AND method='MENU'
-- 执行注意：文件含中文，导入必须使用 --default-character-set=utf8mb4 重定向方式
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 删除公告管理菜单行（type=1） ----------------
DELETE FROM `permission`
WHERE `type` = 1
  AND `code` = 'announcement:manage'
  AND `method` = 'MENU';

-- ---------------- 2. 校验：菜单行已清零，接口规则保留 ----------------
-- leftover：公告管理菜单行残留数，应为 0
SELECT COUNT(*) AS leftover
FROM `permission`
WHERE `type` = 1 AND `code` = 'announcement:manage';

-- interface_rules：type=2 接口规则条数，应为 6（POST / PUT / DELETE / status / top / read-stats）
SELECT COUNT(*) AS interface_rules
FROM `permission`
WHERE `type` = 2 AND `code` = 'announcement:manage';

-- ---------------- 3. 越权脏数据核查（合并前置条件，供上线验收使用） ----------------
-- 核查1：role_permission 中 announcement:manage 的绑定角色（应仅含 ADMIN 等预期角色）
-- SELECT rp.role_id, r.role_name, COUNT(*) AS bind_count
-- FROM `role_permission` rp
-- JOIN `role` r ON r.id = rp.role_id
-- WHERE rp.`permission_code` = 'announcement:manage'
--    OR rp.`permission_id` IN (SELECT id FROM `permission` WHERE `code` = 'announcement:manage')
-- GROUP BY rp.role_id, r.role_name;
-- 核查2：user_role 中是否存在绑定预期外角色的用户（如普通员工绑 ADMIN）
-- SELECT ur.user_id, u.username, ur.role_id, r.role_name
-- FROM `user_role` ur
-- JOIN `role` r ON r.id = ur.role_id
-- JOIN `user` u ON u.id = ur.user_id
-- WHERE r.role_name NOT IN ('ADMIN', 'SUPER_ADMIN');
