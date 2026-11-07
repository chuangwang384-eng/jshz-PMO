-- QUICK-ACCESS-20261107; MySQL 8+ additive, repeatable. Per-user workbench quick access config.
-- Apply only after backup. No data deletion; rollback keeps this table.
CREATE TABLE IF NOT EXISTS user_quick_access (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 item_key VARCHAR(64) NOT NULL,
 sort_order INT NOT NULL DEFAULT 0,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_user_item (user_id, item_key),
 KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台快捷访问个性化配置';
