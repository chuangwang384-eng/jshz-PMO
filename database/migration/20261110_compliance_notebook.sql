-- MySQL 8.x+. 企业合规记事本（2026-11-10 需求/设计文档）增量迁移。
-- Re-runnable; additive only; no legacy row is moved/deleted.
-- Rollback: DROP the 3 new tables and the 3 added columns on compliance_item
-- (keep data if rolling back only the app; old app shows withdrawn rows — legacy entries stay closed).
CREATE TABLE IF NOT EXISTS compliance_item_hide (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cih_user_item (user_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规记事本个人隐藏（仅影响当前员工）';

CREATE TABLE IF NOT EXISTS compliance_project_doc_binding (
  project_id BIGINT NOT NULL PRIMARY KEY,
  document_id BIGINT NOT NULL,
  doc_title VARCHAR(255) NOT NULL COMMENT '绑定时文档名快照',
  bind_by VARCHAR(64) NOT NULL,
  bind_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目×来源文档绑定（首次推送写入，本期无换绑）';

CREATE TABLE IF NOT EXISTS compliance_project_todo (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  source_item_id BIGINT NOT NULL,
  document_id BIGINT NOT NULL,
  document_version_id BIGINT NULL,
  doc_title VARCHAR(255) NULL COMMENT '推送时文档名快照',
  title VARCHAR(255) NOT NULL COMMENT '推送时标题快照',
  content TEXT NULL COMMENT '推送时内容快照',
  source_anchor VARCHAR(255) NULL COMMENT '来源引用（版本号/章节锚点）',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE',
  handle_by VARCHAR(64) NULL,
  handle_time DATETIME NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  row_version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_cpt_project_item (project_id, source_item_id),
  KEY idx_cpt_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目合规待办（来源快照，撤下/更新不影响）';

SET @w := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='compliance_item' AND COLUMN_NAME='withdrawn');
SET @wddl := IF(@w=0,'ALTER TABLE compliance_item ADD COLUMN withdrawn TINYINT NOT NULL DEFAULT 0 COMMENT ''1=已按文档撤下（历史保留）''','SELECT 1');
PREPARE wst FROM @wddl; EXECUTE wst; DEALLOCATE PREPARE wst;
SET @wb := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='compliance_item' AND COLUMN_NAME='withdrawn_by');
SET @wbddl := IF(@wb=0,'ALTER TABLE compliance_item ADD COLUMN withdrawn_by VARCHAR(64) NULL','SELECT 1');
PREPARE wbst FROM @wbddl; EXECUTE wbst; DEALLOCATE PREPARE wbst;
SET @wt := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='compliance_item' AND COLUMN_NAME='withdrawn_time');
SET @wtdl := IF(@wt=0,'ALTER TABLE compliance_item ADD COLUMN withdrawn_time DATETIME NULL','SELECT 1');
PREPARE wtst FROM @wtdl; EXECUTE wtst; DEALLOCATE PREPARE wtst;
SET @widx := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='compliance_item' AND INDEX_NAME='idx_ci_doc_withdrawn');
SET @wixddl := IF(@widx=0,'ALTER TABLE compliance_item ADD INDEX idx_ci_doc_withdrawn (document_id, withdrawn)','SELECT 1');
PREPARE wixst FROM @wixddl; EXECUTE wixst; DEALLOCATE PREPARE wixst;

CREATE TABLE IF NOT EXISTS mig_20261110_notebook_done (
  id TINYINT NOT NULL PRIMARY KEY,
  done_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移完成标记';
INSERT INTO mig_20261110_notebook_done (id) SELECT 1 FROM (SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM mig_20261110_notebook_done WHERE id = 1);
