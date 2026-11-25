package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知免打扰偏好（需求 D，2026-11-25）。
 * 一行 = 用户对某通知类型关闭站内通知（notify 落库前过滤）；
 * password_reset（账号安全）不允许免打扰，保存偏好时由服务端拒绝，本表不会出现该类型。
 */
@Data
@TableName("notification_mute")
public class NotificationMute {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 sys_user.id */
    private Long userId;

    /** 免打扰的通知类型（NotificationService.KNOWN_TYPES 内，password_reset 除外） */
    private String type;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
