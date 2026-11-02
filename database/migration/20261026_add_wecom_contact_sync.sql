-- =============================================================
-- 企微通讯录同步与账号体系改造（2026-10-27 评审通过）
-- 变更：
--   1. 3 张新表：wecom_department / wecom_user_department / wecom_sync_log
--   2. sys_user 加列 mobile（AFTER wecom_userid）、position（AFTER mobile）
--   3. sys_user 加唯一索引 uk_wecom_userid(wecom_userid)、uk_mobile(mobile)
--   4. 删除 sys_user 唯一索引 uk_username（删除前先备份索引定义到
--      wecom_migration_uk_username_backup，作为删除前备份痕迹）
--   5. 接口权限码 wecom:sync（一码多路径）并绑定 ADMIN 角色
-- 幂等：可重复执行（CREATE TABLE IF NOT EXISTS / information_schema 判列判索引 / INSERT IGNORE）
-- 适用数据库：MySQL 8.x（目标库 expert_system；测试演练库 expert_system_restore_test
--             执行时按目标环境替换 USE 语句，生产库仅由部署流程执行）
-- 回滚：见 docs/deployment.md「企微同步回滚」——登录回滚 AUTH_LOGIN_MODE=NAME；
--       uk_username 仅在无重名数据时可重建，重建语句见文件尾部注释。
-- 硬约束：删除 uk_username 前必须先备份（本脚本已在 DROP 前将索引定义写入备份表）。
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 新表：wecom_department（企微部门快照，不回写） ----------------

CREATE TABLE IF NOT EXISTS `wecom_department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dept_id` BIGINT NOT NULL COMMENT '企微部门id（根=1）',
  `name` VARCHAR(100) NOT NULL COMMENT '部门名称',
  `parent_dept_id` BIGINT NULL COMMENT '父部门id（根部门为 NULL）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '部门排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0企微已删除（保留快照）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微部门快照表（只存快照，不回写企微）';

-- ---------------- 2. 新表：wecom_user_department（成员-部门归属，企微一人可属多部门） ----------------

CREATE TABLE IF NOT EXISTS `wecom_user_department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT 'sys_user.id',
  `dept_id` BIGINT NOT NULL COMMENT '企微部门id',
  `is_main` TINYINT NOT NULL DEFAULT 0 COMMENT '1主部门 0非主部门（企微 department 数组第一项为主部门）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_dept` (`user_id`, `dept_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微成员-部门归属表';

-- ---------------- 3. 新表：wecom_sync_log（每次手动/定时同步一条审计） ----------------

CREATE TABLE IF NOT EXISTS `wecom_sync_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trigger_type` VARCHAR(20) NOT NULL COMMENT 'MANUAL/SCHEDULED/DRYRUN',
  `operator` VARCHAR(50) NULL COMMENT '操作人（定时任务为 system）',
  `status` VARCHAR(20) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
  `fetched_users` INT NOT NULL DEFAULT 0 COMMENT '拉取成员数',
  `created_users` INT NOT NULL DEFAULT 0 COMMENT '新建用户数',
  `updated_users` INT NOT NULL DEFAULT 0 COMMENT '更新已绑定用户数',
  `disabled_users` INT NOT NULL DEFAULT 0 COMMENT '停用用户数',
  `bound_users` INT NOT NULL DEFAULT 0 COMMENT '自动绑定存量用户数',
  `unbound_left` INT NOT NULL DEFAULT 0 COMMENT '剩余未绑定存量用户数',
  `error_msg` TEXT NULL COMMENT '失败原因',
  `detail` TEXT NULL COMMENT 'DryRun 变更清单 JSON（前端试算展示）',
  `start_time` DATETIME NULL COMMENT '开始时间',
  `end_time` DATETIME NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微通讯录同步审计日志';

-- ---------------- 4. sys_user 加列 mobile ----------------

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'mobile'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `mobile` VARCHAR(20) NULL COMMENT ''手机号（唯一登录标识，未绑定用户为 NULL）'' AFTER `wecom_userid`',
  'SELECT ''sys_user.mobile already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 4.1 sys_user 加列 position（职务展示，企微 user/get position） ----------------

SET @col_position_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'position'
);
SET @ddl := IF(@col_position_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `position` VARCHAR(100) NULL COMMENT ''职务（企微 user/get position，同步展示用；非登录标识）'' AFTER `mobile`',
  'SELECT ''sys_user.position already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 5. sys_user 加唯一索引 uk_wecom_userid / uk_mobile ----------------

SET @idx_wecom_userid := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_wecom_userid'
);
SET @ddl := IF(@idx_wecom_userid = 0,
  'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_wecom_userid` (`wecom_userid`)',
  'SELECT ''uk_wecom_userid already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_mobile := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_mobile'
);
SET @ddl := IF(@idx_mobile = 0,
  'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_mobile` (`mobile`)',
  'SELECT ''uk_mobile already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 6. 删除 uk_username（先备份索引定义，再判存在后删除） ----------------

-- 6.1 备份 uk_username 索引定义（删除前备份，满足硬约束；重复执行幂等：INSERT IGNORE 由
--     backup 表唯一约束（idx_name, seq_in_index）保证单次写入）
CREATE TABLE IF NOT EXISTS `wecom_migration_uk_username_backup` (
  `backup_id` BIGINT NOT NULL AUTO_INCREMENT,
  `idx_name` VARCHAR(64) NOT NULL COMMENT '索引名',
  `column_name` VARCHAR(64) NOT NULL COMMENT '索引列',
  `seq_in_index` INT NOT NULL COMMENT '列在索引中的序号',
  `non_unique` INT NOT NULL COMMENT '0唯一 1非唯一',
  `index_type` VARCHAR(32) NOT NULL COMMENT '索引类型',
  `backup_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
  PRIMARY KEY (`backup_id`),
  UNIQUE KEY `uk_backup_idx` (`idx_name`, `seq_in_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='uk_username 删除前备份表（回滚重建时参考）';

INSERT IGNORE INTO `wecom_migration_uk_username_backup` (`idx_name`, `column_name`, `seq_in_index`, `non_unique`, `index_type`)
SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE, INDEX_TYPE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_username';

-- 6.2 判存在后删除 uk_username（幂等：已删除则跳过）
SET @idx_username := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_username'
);
SET @ddl := IF(@idx_username > 0,
  'ALTER TABLE `sys_user` DROP INDEX `uk_username`',
  'SELECT ''uk_username already dropped'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 7. 接口权限码 wecom:sync（一码多路径，参照 approval 模块范式） ----------------

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('wecom:sync', '企微同步-触发同步', 2, 'POST', '/api/admin/wecom/sync'),
('wecom:sync', '企微同步-同步状态', 2, 'GET', '/api/admin/wecom/sync/status'),
('wecom:sync', '企微同步-最近记录', 2, 'GET', '/api/admin/wecom/sync/recent'),
('wecom:sync', '企微同步-部门树', 2, 'GET', '/api/admin/wecom/departments'),
('wecom:sync', '企微同步-未绑定清单', 2, 'GET', '/api/admin/wecom/unbound'),
('wecom:sync', '企微同步-人工指认绑定', 2, 'POST', '/api/admin/users/*/bind-wecom');

-- 绑定 ADMIN 角色（按 role.code 查询 role_id，禁止硬编码）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'wecom:sync' FROM `role` r WHERE r.code = 'ADMIN';

-- ---------------- 8. 回滚参考（非执行语句，仅文档） ----------------
-- 登录回滚：AUTH_LOGIN_MODE=NAME（改 NSSM 环境变量重启后端）；
-- 功能回滚：sync-cron-enabled=false；
-- uk_username 重建（仅在确认 sys_user.username 无重名时执行）：
--   ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_username` (`username`);
--   重建前可用备份表核对原索引定义：
--   SELECT * FROM wecom_migration_uk_username_backup WHERE idx_name='uk_username';
