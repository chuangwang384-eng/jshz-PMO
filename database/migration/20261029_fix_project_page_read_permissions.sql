-- 20261029_fix_project_page_read_permissions.sql
-- 适用：MySQL expert_system（1.007 基线）
-- 背景：项目管理列表页加载时会读取 GET /api/project/config/phases、/config/types 作为筛选下拉，
--       原规则 GET /api/project/config/** -> project:manage 把字典读取也拦下，
--       导致仅有 project:view 的用户（如 EMPLOYEE）打开项目管理页即弹"无权限执行该操作"。
-- 变更：删除该 GET 读取规则，配置字典读取改为登录即可；config 下的写操作（POST/PUT/DELETE）仍需 project:manage，不受影响。
-- 幂等：可重复执行。
-- 回滚：INSERT 对应规则行即可恢复（见文末注释）。

DELETE FROM permission
WHERE type = 2 AND method = 'GET' AND path = '/api/project/config/**'
  AND code = 'project:manage';

-- 验证：应返回 0 行
SELECT method, path, code FROM permission
WHERE type = 2 AND method = 'GET' AND path LIKE '/api/project/config%';

-- 回滚参考（如需恢复原规则）：
-- INSERT INTO permission (type, method, path, code, name, create_time)
-- VALUES (2, 'GET', '/api/project/config/**', 'project:manage', '项目配置读取', NOW());
