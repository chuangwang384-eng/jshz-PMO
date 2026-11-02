-- =============================================================
-- 2026-10-28 系统公告模块：announcement + announcement_read + 权限码 announcement:manage
-- 幂等：CREATE TABLE IF NOT EXISTS / INSERT IGNORE / INSERT...SELECT...WHERE NOT EXISTS，可重复执行
-- 设计要点：
--   1) announcement.content 为富文本正文，入库前经后端 XSS 白名单清洗（com.jshz.expert.util.XssCleanUtil）
--   2) status: ONLINE 上架 / OFFLINE 下架；expire_time 为空 = 永久有效
--   3) 首页生效列表条件：status=ONLINE AND effective_time<=now AND (expire_time IS NULL OR expire_time>now)
--   4) announcement_read 唯一联合键 (announcement_id,user_id) 保证标记已读幂等
--   5) announcement:manage 绑定 ADMIN；SUPER_ADMIN 代码内强制全权不落绑定（与既有惯例一致）
-- 执行注意：文件含中文，导入必须使用 --default-character-set=utf8mb4 重定向方式
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 公告主表 ----------------
CREATE TABLE IF NOT EXISTS `announcement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content` MEDIUMTEXT NOT NULL COMMENT '富文本正文（XSS 白名单清洗后入库）',
  `effective_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间（到达后才在首页/生效列表展示）',
  `expire_time` DATETIME DEFAULT NULL COMMENT '失效时间（空=永久有效）',
  `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '置顶标记：0 否 / 1 是',
  `status` VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '状态：ONLINE 上架 / OFFLINE 下架',
  `creator_id` BIGINT DEFAULT NULL COMMENT '发布人 sys_user.id',
  `creator_name` VARCHAR(50) DEFAULT NULL COMMENT '发布人用户名快照',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_announce_status_time` (`status`, `effective_time`, `create_time`),
  KEY `idx_announce_top` (`is_top`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告主表';

-- ---------------- 2. 公告已读表 ----------------
CREATE TABLE IF NOT EXISTS `announcement_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `announcement_id` BIGINT NOT NULL COMMENT '公告 announcement.id',
  `user_id` BIGINT NOT NULL COMMENT '已读用户 sys_user.id',
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`),
  KEY `idx_read_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读记录（唯一联合键保证幂等）';

-- ---------------- 3. 权限码：announcement:manage ----------------
-- 菜单行 type=1（MENU）+ 接口行 type=2（method+path Ant 模式，供 DbPermissionFilter 匹配）
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `method`, `path`) VALUES
('announcement:manage', '公告管理', 1, 'MENU', 'announcement:manage'),
('announcement:manage', '公告发布', 2, 'POST', '/api/announcement'),
('announcement:manage', '公告编辑', 2, 'PUT', '/api/announcement/*'),
('announcement:manage', '公告删除', 2, 'DELETE', '/api/announcement/*'),
('announcement:manage', '公告上下架', 2, 'PUT', '/api/announcement/*/status'),
('announcement:manage', '公告置顶', 2, 'PUT', '/api/announcement/*/top'),
('announcement:manage', '公告已读统计', 2, 'GET', '/api/announcement/*/read-stats');

-- ---------------- 4. 绑定 ADMIN（SUPER_ADMIN 代码全权，不落绑定） ----------------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'announcement:manage'
FROM `role` r
WHERE r.`code` = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT rp.role_id FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = 'announcement:manage' LIMIT 1) AS x);
