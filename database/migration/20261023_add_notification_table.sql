-- =============================================================
-- 通用消息通知中心：1 张表
-- 幂等：可重复执行
-- 设计依据：docs/design/2026-10-23-知识星球菜单交互与永久删除及消息通知中心-设计文档.md
-- 对应功能：审批结果 / 新申请待审 / 角色调整 / 密码重置 / 文档下架与永久删除 五类事件通知
-- =============================================================
USE `expert_system`;

-- ---------------- 1. 消息通知表 ----------------

CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '接收人 sys_user.id',
  `type` VARCHAR(50) NOT NULL COMMENT '事件类型（approval_result/approval_todo/role_changed/password_reset/doc_off_shelf/doc_deleted）',
  `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '通知内容',
  `jump_path` VARCHAR(255) DEFAULT NULL COMMENT '点击跳转前端路由（如 /profile/my-approvals?status=1）',
  `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用消息通知表';
