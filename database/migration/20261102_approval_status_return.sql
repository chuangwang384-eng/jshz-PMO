-- =============================================================
-- 审批流配置板块改造（R4 退回修改：状态与动作约定扩展）
-- 设计依据：docs/2026-10-31-审批流配置板块改造-design.md（2.4 状态机）
-- 内容：结构不变，仅扩展列注释（约定层）
--   application.status：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 退回修改
--   approval_record.action：SUBMIT/APPROVE/REJECT/WITHDRAW/TRANSFER/ADD_APPROVER/
--                           RETURN（退回修改）/RESUBMIT（重新提交）
-- 幂等：MODIFY COLUMN 可重复执行，不改变数据与约束
-- =============================================================
USE `expert_system`;

ALTER TABLE `application`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0
  COMMENT '0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 退回修改';

ALTER TABLE `approval_record`
  MODIFY COLUMN `action` VARCHAR(20) NOT NULL
  COMMENT '动作：SUBMIT/APPROVE/REJECT/WITHDRAW/TRANSFER/ADD_APPROVER/RETURN/RESUBMIT';
