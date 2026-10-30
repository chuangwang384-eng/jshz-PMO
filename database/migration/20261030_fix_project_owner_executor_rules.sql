-- 20261030_fix_project_owner_executor_rules.sql
-- 适用：MySQL expert_system（1.010 基线）
-- 背景：项目级操作（资料/预算/合同，业务层授权为 isOwnerOrExecutor 即负责人/执行人/超管）
--       在 permission 表被错误映射到 project:manage 权限码，导致：
--       - 执行人/负责人（无 project:manage）调用这些接口被 DbPermissionFilter 层 403 拦截，
--         即便业务层 isOwnerOrExecutor 本应放行（双向失效的另一半）；
--       - 前端按钮已改为 canAdvance（owner/executor 语义）显示，但后端 filter 仍拦，功能不可达。
-- 变更：删除以下 6 条"项目级操作"接口的 project:manage 规则，改由业务层 isOwnerOrExecutor 裁决：
--   PUT    /api/project/*/budget                -> registerBudget（isOwnerOrExecutor）
--   POST   /api/project/*/docs                  -> uploadDoc（isOwnerOrExecutor）
--   DELETE /api/project/*/docs/*                -> deleteDoc（isOwnerOrExecutor）
--   PUT    /api/project/*/docs/richtext         -> saveRichText（isOwnerOrExecutor）
--   POST   /api/project/*/contracts             -> createContract（isOwnerOrExecutor）
--   PUT    /api/project/*/contracts/*/sensitive -> updateContractSensitive（isOwnerOrExecutor）
-- 保留不动（业务层 requireManage，仍需 project:manage）：编辑/删除项目、调派专家/新建专家卡/移出专家/劳务费、
--   关联供应商/移出供应商/供应商劳务费、config 全部写操作。
-- 幂等：可重复执行。
-- 回滚：见文末 INSERT 参考。

DELETE FROM permission
WHERE type = 2 AND code = 'project:manage'
  AND (method, path) IN (
    ('PUT',    '/api/project/*/budget'),
    ('POST',   '/api/project/*/docs'),
    ('DELETE', '/api/project/*/docs/*'),
    ('PUT',    '/api/project/*/docs/richtext'),
    ('POST',   '/api/project/*/contracts'),
    ('PUT',    '/api/project/*/contracts/*/sensitive')
  );

-- 验证：应返回 0 行
SELECT method, path, code FROM permission
WHERE type = 2 AND code = 'project:manage'
  AND (method, path) IN (
    ('PUT','/api/project/*/budget'),('POST','/api/project/*/docs'),
    ('DELETE','/api/project/*/docs/*'),('PUT','/api/project/*/docs/richtext'),
    ('POST','/api/project/*/contracts'),('PUT','/api/project/*/contracts/*/sensitive')
  );

-- 回滚参考：
-- INSERT INTO permission (type, method, path, code, name, create_time) VALUES
--   (2,'PUT','/api/project/*/budget','project:manage','项目预算登记',NOW()),
--   (2,'POST','/api/project/*/docs','project:manage','项目资料上传',NOW()),
--   (2,'DELETE','/api/project/*/docs/*','project:manage','项目资料删除',NOW()),
--   (2,'PUT','/api/project/*/docs/richtext','project:manage','项目文本资料',NOW()),
--   (2,'POST','/api/project/*/contracts','project:manage','项目合同登记',NOW()),
--   (2,'PUT','/api/project/*/contracts/*/sensitive','project:manage','项目合同敏感字段',NOW());
