-- =============================================================
-- 默认普通员工角色迁移：存量无角色用户补绑 EMPLOYEE
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS，可重复执行
-- 设计依据：docs/superpowers/specs/2026-10-26-default-employee-role-design.md
-- 说明：仅补绑指定 3 个存量无角色用户（宋庆鑫154 / 测试156 / 张三205）；
--       已绑定任何角色的用户不受影响；(user_id, role_id) 已存在则跳过
-- =============================================================
USE `expert_system`;

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `sys_user` u
JOIN `role` r ON r.`code` = 'EMPLOYEE'
WHERE u.id IN (154, 156, 205)
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` ur WHERE ur.user_id = u.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` ur2 WHERE ur2.user_id = u.id AND ur2.role_id = r.id
  );
