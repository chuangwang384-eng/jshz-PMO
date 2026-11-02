-- ============================================================
-- 20261102_project_phase_history.sql
-- 项目板块四项需求·需求三：阶段变更历史表（推进/回退含跨级，可追溯）
-- 适用：MySQL 8.x（expert_system）；幂等（IF NOT EXISTS）；可重复执行
-- 回滚：DROP TABLE IF EXISTS project_phase_history;（纯新增表，无业务数据依赖）
-- ============================================================

CREATE TABLE IF NOT EXISTS `project_phase_history` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`     BIGINT NOT NULL COMMENT '项目ID（project.id）',
  `from_phase_id`  BIGINT NOT NULL COMMENT '原阶段（project_phase.id）',
  `to_phase_id`    BIGINT NOT NULL COMMENT '目标阶段（project_phase.id）',
  `action_type`    VARCHAR(16) NOT NULL COMMENT '动作：ADVANCE 推进 / ROLLBACK 回退（含跨级）',
  `reason`         VARCHAR(500) DEFAULT NULL COMMENT '原因（回退必填，推进为空）',
  `operator_id`    BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name`  VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
  `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_phase_hist_project` (`project_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段变更历史（推进/回退含跨级）';

-- 核对：SHOW CREATE TABLE project_phase_history;
