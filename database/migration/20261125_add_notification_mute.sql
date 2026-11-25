-- 20261125 消息免打扰偏好表（需求 D；幂等，可重复执行）
-- 用途：按 (user_id, type) 记录用户不接收的通知类型；NotificationService.notify 落库前过滤。
-- 口径：password_reset（账号安全）不允许免打扰，由服务端在保存偏好时拒绝，表中不会出现。
-- 顺序：先迁移再发布 1.0.142 后端（新代码读写该表；旧代码不感知，先迁移安全）。

CREATE TABLE IF NOT EXISTS `notification_mute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '接收人 sys_user.id',
  `type` varchar(50) NOT NULL COMMENT '免打扰的通知类型（NotificationService.KNOWN_TYPES 内，password_reset 除外）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type` (`user_id`,`type`),
  CONSTRAINT `fk_notif_mute_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知免打扰偏好（按类型）';
