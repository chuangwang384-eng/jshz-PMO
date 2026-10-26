-- =============================================================
-- R-B4 审批申请字段模板：1 张新表 + expert_change_request 加 1 列 + 接口权限码
-- 幂等：可重复执行（CREATE TABLE IF NOT EXISTS / information_schema 判列 / INSERT IGNORE）
-- 约束：模板字段必须来自专家可编辑白名单（WHITELIST_FIELDS + PHOTO_FIELDS，后端代码校验）；
--       停用模板不可再用于新申请（提交时校验 enabled）。
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 模板表 ----------------

CREATE TABLE IF NOT EXISTS `approval_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称（唯一）',
  `fields` TEXT NOT NULL COMMENT '模板字段 JSON 数组（中文键，⊆ 专家可编辑白名单+照片字段）',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（停用后不可用于新申请）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批申请字段模板表';

-- ---------------- 2. expert_change_request 加列 template_id ----------------
-- 记录申请来源模板（可空=未使用模板，历史数据兼容；模板删除后保留原值仅作来源记录）

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'expert_change_request'
    AND COLUMN_NAME = 'template_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `expert_change_request` ADD COLUMN `template_id` BIGINT NULL COMMENT ''来源模板 approval_template.id（可空）'' AFTER `request_type`',
  'SELECT ''template_id already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------- 3. 接口权限码（approval:config 复用，一码多路径） ----------------

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('approval:config', '审批模板-列表(全部)', 2, 'GET', '/api/approval/templates/all'),
('approval:config', '审批模板-新增', 2, 'POST', '/api/approval/templates'),
('approval:config', '审批模板-修改/启停', 2, 'PUT', '/api/approval/templates/**'),
('approval:config', '审批模板-删除', 2, 'DELETE', '/api/approval/templates/**');

-- ---------------- 4. 默认模板种子（3 个） ----------------
-- 「专家新增」覆盖 CREATE_REQUIRED_FIELDS 必填项；照片类模板按常用场景预设

INSERT IGNORE INTO `approval_template` (`template_name`, `fields`, `enabled`) VALUES
('联系方式变更', '["联系方式"]', 1),
('职称变更', '["职称","单位","科室"]', 1),
('专家新增', '["姓名","单位","科室","职称","研究方向","擅长领域","联系方式","照片","身份证正面","身份证反面"]', 1);
