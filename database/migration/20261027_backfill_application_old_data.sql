-- =============================================================
-- 审批「申请前后变化」对比：application.biz_data.oldData 旧值快照回填（一次性）
-- 设计依据：docs/design/2026-10-26-审批界面申请前后变化对比-设计文档.md（需求 V1.0）
-- 幂等：可重复执行（oldData 已存在的行不再回填；无 DDL，仅 UPDATE 补 JSON 子键）
-- 适用数据库：MySQL 8.x；执行方式：cmd 字节级重定向 + --default-character-set=utf8mb4（禁 PowerShell 管道，见 docs/deployment.md 运维警告 2）
-- 背景：新提交的修改申请由后端 ApplicationService.submitRequest 写入 biz_data.oldData 全量快照；
--   存量单分状态处理：
--   - status=0 待审批 / 2 已驳回 / 3 已撤回：expert_info 未被该单更新，用专家当前值回填（可准确还原「变更前」）
--   - status=1 已通过：专家表已被更新、旧值不可还原，保持为空（前端显示「历史单无快照」提示）
--   - EXPERT_CREATE 新增申请：无「变更前」概念，不回填
-- 回滚（如需）：UPDATE application SET biz_data = JSON_REMOVE(biz_data, '$.oldData');
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 回填预览（只读，核对影响行数） ----------------
SELECT a.`id`, a.`status`, a.`applicant`,
       JSON_UNQUOTE(JSON_EXTRACT(a.`biz_data`, '$.expertName')) AS expert_name,
       e.`name` AS expert_info_name
FROM `application` a
JOIN `expert_info` e
  ON e.`id` = CAST(JSON_UNQUOTE(JSON_EXTRACT(a.`biz_data`, '$.expertId')) AS UNSIGNED)
WHERE a.`application_type` = 'EXPERT_MODIFY'
  AND a.`status` IN (0, 2, 3)
  AND JSON_VALID(a.`biz_data`)
  AND JSON_EXTRACT(a.`biz_data`, '$.oldData') IS NULL;

-- ---------------- 2. 回填 oldData（11 文本字段 + 3 照片文件名，键序与后端 buildOldSnapshot 一致） ----------------
UPDATE `application` a
JOIN `expert_info` e
  ON e.`id` = CAST(JSON_UNQUOTE(JSON_EXTRACT(a.`biz_data`, '$.expertId')) AS UNSIGNED)
SET a.`biz_data` = JSON_SET(a.`biz_data`, '$.oldData', JSON_OBJECT(
  '姓名', e.`name`,
  '单位', e.`unit`,
  '科室', e.`department`,
  '职称', e.`title`,
  '研究方向', e.`research_direction`,
  '擅长领域', e.`expertise_area`,
  '联系方式', e.`contact`,
  '简介', e.`profile`,
  '身份证号', e.`id_card`,
  '银行卡号', e.`bank_card`,
  '银行卡开户行', e.`bank_branch`,
  '照片', e.`photo`,
  '身份证正面', e.`id_card_front`,
  '身份证反面', e.`id_card_back`
))
WHERE a.`application_type` = 'EXPERT_MODIFY'
  AND a.`status` IN (0, 2, 3)
  AND JSON_VALID(a.`biz_data`)
  AND JSON_EXTRACT(a.`biz_data`, '$.oldData') IS NULL;

-- ---------------- 3. 回填结果核验（只读：应全部为已回填行数） ----------------
SELECT a.`status`, COUNT(*) AS total,
       SUM(CASE WHEN JSON_EXTRACT(a.`biz_data`, '$.oldData') IS NOT NULL THEN 1 ELSE 0 END) AS with_old_data
FROM `application` a
WHERE a.`application_type` = 'EXPERT_MODIFY'
  AND a.`status` IN (0, 2, 3)
GROUP BY a.`status`;
