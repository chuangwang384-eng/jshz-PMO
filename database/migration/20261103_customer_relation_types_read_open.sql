-- ============================================================
-- 20261103_customer_relation_types_read_open.sql
-- 修复：测试账号进入客户信息板块弹「无权限执行该操作」
-- 根因：permission id=221 将只读接口 GET /api/customer/relations/types 绑定 customer:manage，
--       而客户信息列表页（CustomerInfo.vue）onMounted 即拉取关系类型字典 → 无 manage 权限账号 403 弹窗。
-- 修复：删除该 GET 规则（回归「GET 类查询无规则=登录即可放行」的全局模式，见 20261027/20261101 先例）；
--       写操作（POST/PUT /relations/types）维持 customer:manage 不变。
-- 幂等：DELETE-if-exists；可重复执行。执行后需重启 ExpertSystemBackend 重载权限规则缓存。
-- 回滚：INSERT IGNORE INTO permission (code,name,type,method,path)
--       VALUES ('customer:manage','客户关系类型列表',2,'GET','/api/customer/relations/types');
-- ============================================================
USE `expert_system`;

DELETE FROM `permission`
WHERE `method` = 'GET' AND `path` = '/api/customer/relations/types';

-- 核对：SELECT id,code,method,path FROM permission WHERE path='/api/customer/relations/types';  -- 应为空
