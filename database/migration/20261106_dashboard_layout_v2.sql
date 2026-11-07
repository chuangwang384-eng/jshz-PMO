-- MySQL 8+ additive migration. Keep both columns when reverting the application.
SET @dashboard_layout_v2_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'user_dashboard_layout' AND column_name = 'layout_v2'),
  'DO 0',
  'ALTER TABLE user_dashboard_layout ADD COLUMN layout_v2 JSON NULL'
);
PREPARE dashboard_layout_v2_stmt FROM @dashboard_layout_v2_sql;
EXECUTE dashboard_layout_v2_stmt;
DEALLOCATE PREPARE dashboard_layout_v2_stmt;
