package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用消息通知实体（消息通知中心）。
 * 对应数据库表 notification。
 * 字段与列名一致（camelCase ↔ underscore 由 MyBatis-Plus 自动映射）。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 sys_user.id */
    private Long userId;

    /** 事件类型：approval_result / approval_todo / role_changed / password_reset / doc_off_shelf / doc_deleted */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 点击跳转前端路由（如 /profile/my-approvals?status=1） */
    private String jumpPath;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
