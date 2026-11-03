-- MySQL 8.x+. Additive rollout gate; select the approved target DB before execution.
-- No production execution in this task. Re-runnable; no legacy row is moved/deleted.
CREATE TABLE IF NOT EXISTS compliance_user_mode (
 user_id BIGINT NOT NULL PRIMARY KEY,
 mode VARCHAR(8) NOT NULL DEFAULT 'V3',
 legacy_snapshot JSON NULL COMMENT '切换时只读旧进度快照，不自动关联项目',
 switched_at DATETIME NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规个人切换开关；缺行沿用V3';
SET @legacy_col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='compliance_user_mode' AND COLUMN_NAME='legacy_snapshot');
SET @legacy_ddl := IF(@legacy_col=0,'ALTER TABLE compliance_user_mode ADD COLUMN legacy_snapshot JSON NULL','SELECT 1');
PREPARE legacy_stmt FROM @legacy_ddl; EXECUTE legacy_stmt; DEALLOCATE PREPARE legacy_stmt;
-- Rollback: switch UI/app back only after stopping V4 scheduling; preserve this gate
-- and all execution data. Do not DROP tables that contain user data.
