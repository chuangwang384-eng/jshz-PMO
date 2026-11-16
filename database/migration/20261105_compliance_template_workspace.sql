-- COMPLIANCE-VISUAL-20261105; MySQL 8+ additive expansion, repeatable.
-- Apply only after backup and target metadata inspection. No data deletion.
CREATE TABLE IF NOT EXISTS compliance_template_workspace (
 document_id BIGINT PRIMARY KEY,
 content JSON NOT NULL,
 row_version INT NOT NULL DEFAULT 0,
 selection_resolved TINYINT NOT NULL DEFAULT 0,
 active_template_version_id BIGINT NULL,
 active_content_hash CHAR(64) NULL,
 updated_by BIGINT NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS compliance_template_workspace_request (
 document_id BIGINT NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 payload_hash CHAR(64) NOT NULL, actor_user_id BIGINT NULL,
 result JSON NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(document_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Rollback: retain these tables; use a compatible backend. Never drop referenced history.
CREATE TABLE IF NOT EXISTS compliance_template_extraction (
 id VARCHAR(36) PRIMARY KEY, document_id BIGINT NOT NULL, source_version INT NOT NULL,
 result JSON NOT NULL, confirmed_by BIGINT NULL, confirmed_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_workspace_extraction_document(document_id,source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS compliance_template_review (
 document_id BIGINT NOT NULL, node_code VARCHAR(64) NOT NULL, content_hash CHAR(64) NOT NULL,
 reviewed_by BIGINT NOT NULL, reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(document_id,node_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
