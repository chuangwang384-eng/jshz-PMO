-- DASHBOARD-LAYOUT-20261105; MySQL 8+ additive, repeatable. Per-user workbench layout persistence.
-- Apply only after backup. No data deletion; rollback keeps this table.
CREATE TABLE IF NOT EXISTS user_dashboard_layout (
 user_id BIGINT PRIMARY KEY,
 layout JSON NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
