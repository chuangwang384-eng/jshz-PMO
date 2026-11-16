-- Execute only AFTER successful offline backfill and business-difference review.
-- MySQL 8+. DDL commits implicitly; verify backup and migration evidence first.
-- Inspect: SELECT sort,COUNT(*) FROM project_type GROUP BY sort HAVING COUNT(*)>1;
-- Inspect: SELECT COUNT(*) FROM project p LEFT JOIN project_config_snapshot s ON p.id=s.project_id WHERE s.project_id IS NULL;
SET @exists_idx=(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_type' AND INDEX_NAME='uk_project_type_position');
SET @ddl=IF(@exists_idx=0,'ALTER TABLE project_type MODIFY sort INT NOT NULL, ADD UNIQUE KEY uk_project_type_position(sort)','SELECT 1');
PREPARE pc_stmt FROM @ddl; EXECUTE pc_stmt; DEALLOCATE PREPARE pc_stmt;
