-- MySQL 8.x+. 旧执行清单四表清理（需求规则6，Owner 口径：测试数据，无需备份）。
-- ⚠️ 高风险不可恢复：先在目标库执行上方 COUNT 核对行数并取得 Owner 批准后再执行 DELETE 段。
-- 执行顺序：reminder → event → task → execution（依赖顺序）。
-- 保留：compliance_item、compliance_item_history、compliance_user_progress、compliance_item_assignee、
--       compliance_reminder_log、模板/工作区/收藏/文档等全部其他数据。
-- 回滚：不可恢复（无备份）。应用侧写入口已在 1.0.37.34 关闭，清理前后均不会产生新旧行。

SELECT 'compliance_execution_reminder' AS tbl, COUNT(*) AS cnt FROM compliance_execution_reminder
UNION ALL SELECT 'compliance_execution_event', COUNT(*) FROM compliance_execution_event
UNION ALL SELECT 'compliance_execution_task', COUNT(*) FROM compliance_execution_task
UNION ALL SELECT 'compliance_execution', COUNT(*) FROM compliance_execution;

-- ===== 以下为 DELETE 段（批准后整段执行）=====
DELETE FROM compliance_execution_reminder
WHERE execution_id IN (SELECT id FROM compliance_execution);
DELETE FROM compliance_execution_event
WHERE execution_id IN (SELECT id FROM compliance_execution);
DELETE FROM compliance_execution_task
WHERE execution_id IN (SELECT id FROM compliance_execution);
DELETE FROM compliance_execution;

SELECT 'compliance_execution_reminder' AS tbl, COUNT(*) AS cnt FROM compliance_execution_reminder
UNION ALL SELECT 'compliance_execution_event', COUNT(*) FROM compliance_execution_event
UNION ALL SELECT 'compliance_execution_task', COUNT(*) FROM compliance_execution_task
UNION ALL SELECT 'compliance_execution', COUNT(*) FROM compliance_execution;
