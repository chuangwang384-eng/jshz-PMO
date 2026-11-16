-- PC-20261104, MySQL 8+ / InnoDB. Additive, repeatable schema expansion only.
-- Back up first. Do NOT run against production without separate migration approval.
-- Order: this file -> offline ProjectConfigurationMigration runner -> finalize file.
-- Keep configuration and project-create writes closed throughout mixed-version migration.
CREATE TABLE IF NOT EXISTS project_config_state (
 id BIGINT PRIMARY KEY, epoch BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO project_config_state(id,epoch) VALUES (1,0);
CREATE TABLE IF NOT EXISTS project_type_config_revision (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, type_id BIGINT NOT NULL, revision BIGINT NOT NULL,
 schema_version INT NOT NULL, definition JSON NOT NULL, created_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_type_revision(type_id,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS project_config_snapshot (
 project_id BIGINT PRIMARY KEY, source_revision_id BIGINT NULL, schema_version INT NOT NULL,
 effective_config JSON NOT NULL, config_hash CHAR(64) NOT NULL, origin VARCHAR(32) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS project_config_request (
 request_id VARCHAR(64) PRIMARY KEY, payload_hash CHAR(64) NOT NULL, result JSON NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @exists_col=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project_type' AND COLUMN_NAME='active_config_revision_id');
SET @ddl=IF(@exists_col=0,'ALTER TABLE project_type ADD COLUMN active_config_revision_id BIGINT NULL','SELECT 1');
PREPARE pc_stmt FROM @ddl; EXECUTE pc_stmt; DEALLOCATE PREPARE pc_stmt;
-- No destructive downgrade. Retain these tables when rolling back to a snapshot-aware build.
