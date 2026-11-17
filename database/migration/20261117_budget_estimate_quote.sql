-- MySQL 8+. Preserve this schema when rolling application code back.
SET @budget_estimate_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_budget' AND COLUMN_NAME = 'estimate_inputs');
SET @budget_estimate_ddl = IF(@budget_estimate_exists = 0,
  'ALTER TABLE project_budget ADD COLUMN estimate_inputs JSON NULL',
  'SELECT 1');
PREPARE budget_estimate_stmt FROM @budget_estimate_ddl;
EXECUTE budget_estimate_stmt;
DEALLOCATE PREPARE budget_estimate_stmt;

CREATE TABLE IF NOT EXISTS project_budget_quote (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  request_key VARCHAR(36) NOT NULL,
  doc_id BIGINT NOT NULL,
  snapshot JSON NOT NULL,
  client_note VARCHAR(1000),
  created_by BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  customer_name VARCHAR(100),
  customer_confirmed_at DATETIME,
  customer_remark VARCHAR(1000),
  evidence_doc_id BIGINT,
  recorded_by BIGINT,
  recorded_at DATETIME,
  UNIQUE KEY uk_budget_quote_version (project_id, version_no),
  UNIQUE KEY uk_budget_quote_request (project_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
