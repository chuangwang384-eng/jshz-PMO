-- =============================================================
-- 20261116_budget_stage.sql
-- 项目流程新增「会议预算制作」阶段（位于「日程上传」之前，sort=98）
-- 内容（全部幂等，可重复执行）：
--   1. project_phase 追加内置阶段「会议预算制作」sort=98 is_active=1
--   2. 新表 project_budget            项目报价单/预算单（一项目一份）
--   3. 新表 project_budget_item       预算明细行（分节：酒店/交通/搭建/物料/媒体/礼品/人员/线上报价）
--   4. 新表 project_budget_fee_standard 收费标准默认值（管理员可改，种子 INSERT IGNORE）
-- 回滚：DROP TABLE project_budget_item; DROP TABLE project_budget;
--       DROP TABLE project_budget_fee_standard;
--       DELETE FROM project_phase WHERE name='会议预算制作';
--       DROP TABLE mig_20261116_budget_stage_done;
-- =============================================================
USE `expert_system`;

-- ---------- 1. 内置阶段（表结构 name/sort/is_active） ----------
SET @name_exists = (SELECT COUNT(*) FROM `project_phase` WHERE `name`='会议预算制作');
SET @sql = IF(@name_exists=0,
  'INSERT INTO `project_phase` (`name`,`sort`,`is_active`) VALUES (''会议预算制作'',98,1)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. project_budget ----------
CREATE TABLE IF NOT EXISTS `project_budget` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` BIGINT NOT NULL COMMENT '项目 id',
  `category` VARCHAR(20) NOT NULL COMMENT '大类快照：ONLINE 线上 / OFFLINE 线下',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / CONFIRMED 已确认',
  `total_amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '明细合计',
  `remark` VARCHAR(1000) NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_pb_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目会议预算/报价单';

-- ---------- 3. project_budget_item ----------
CREATE TABLE IF NOT EXISTS `project_budget_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `budget_id` BIGINT NOT NULL,
  `project_id` BIGINT NOT NULL COMMENT '冗余便于按项目查',
  `section` VARCHAR(20) NOT NULL COMMENT 'HOTEL 酒店/TRAFFIC 交通/BUILD 搭建/MATERIAL 物料/MEDIA 媒体/GIFT 礼品/STAFF 人员/ONLINE_QUOTE 线上报价',
  `item_key` VARCHAR(50) NULL COMMENT '收费标准 fee_key 关联（预填项）；NULL=手工行',
  `name` VARCHAR(200) NOT NULL COMMENT '费用项名称',
  `quantity` DECIMAL(10,2) NOT NULL DEFAULT 1,
  `unit` VARCHAR(50) NULL COMMENT '场/人/天/张/个等',
  `unit_price` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT 'quantity*unit_price（服务端计算）',
  `remark` VARCHAR(500) NULL,
  `sort` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_pbi_budget` (`budget_id`, `section`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目预算明细行';

-- ---------- 4. project_budget_fee_standard 收费标准（管理员项目配置页维护） ----------
CREATE TABLE IF NOT EXISTS `project_budget_fee_standard` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `fee_key` VARCHAR(50) NOT NULL COMMENT '稳定键，预算明细 item_key 引用',
  `name` VARCHAR(200) NOT NULL,
  `default_value` VARCHAR(200) NULL COMMENT '默认值；区间类存低,高（如 300,400）；NULL=视需求报价无默认',
  `unit` VARCHAR(50) NULL COMMENT '场/人天/张/个/元等',
  `remark` VARCHAR(500) NULL,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_pbfs_key` (`fee_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议预算收费标准默认值';

-- 种子（管理员可改值/停用，不得删除键语义）
INSERT IGNORE INTO `project_budget_fee_standard` (`fee_key`,`name`,`default_value`,`unit`,`remark`) VALUES
('MC_INTERLUDE',    '串场',                '500',   '元/场',   '公司收取'),
('MC_LETTER',       '沟通函及延展',        '500',   '元/场',   '公司收取'),
('AGENDA_UP',       '日程上浮',            '5,10',  '张',      '参会人员基础上浮张数'),
('MAT_DESKCARD_UP', '台卡上浮',            '10,20', '张',      '参会人员基础上浮张数'),
('MAT_STAND',       '展架（易拉宝）',      '4',     '个/场',   '按每场 4 个易拉宝匹配'),
('MAT_BANNER',      '条幅',                NULL,    '',        '按酒店提供尺寸，默认写真布，视需求报价'),
('MAT_PRINT_OTHER', '其他打印物料',        '500',   '元',      '按 500 预估'),
('MAT_ROSTRUM_FLOWER','讲台花',            '300,400','元',     '按 300-400 预估'),
('MAT_FLOWER_OTHER','桌花及其他花束',      NULL,    '',        '视需求报价'),
('MAT_DESIGN',      '物料设计费',          NULL,    '',        '视需求报价'),
('GIFT_UNIT',       '礼品单价',            '200,300','元/份',  '和客户确认是否需要，按 200-300 预估'),
('STAFF_DAY',       '人员费',              '500',   '元/人/天','固定费用；交通/住宿据实报销'),
('STAFF_TRAVEL',    '人员交通/住宿',       NULL,    '',        '据实报销，不做预算预估');

-- ---------- 5. 标记表（供发布脚本校验；脚本本身幂等） ----------
CREATE TABLE IF NOT EXISTS `mig_20261116_budget_stage_done` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `done_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
