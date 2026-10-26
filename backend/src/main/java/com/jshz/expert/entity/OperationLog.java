package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户敏感操作日志实体（对应数据库表 operation_log）。
 * 记录范围：下载/导出、专家/知识文档敏感写操作、登录/登出/改密/角色权限变更等。
 * action_type 统一枚举见 OperationLogService 常量。
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 操作人 sys_user.id（可空） */
    @JsonProperty("userId")
    private Long userId;

    /** 操作人用户名 */
    @JsonProperty("username")
    private String username;

    /** 操作类型枚举：DOWNLOAD / LOGIN / LOGOUT / CHANGE_PASSWORD 等 */
    @JsonProperty("actionType")
    private String actionType;

    /** 操作描述（含操作对象中文名） */
    @JsonProperty("actionDesc")
    private String actionDesc;

    /** 对象类型：USER / ROLE / EXPERT / DOC 等 */
    @JsonProperty("targetType")
    private String targetType;

    /** 对象标识（id 或用户名） */
    @JsonProperty("targetId")
    private String targetId;

    /** 来源 IP */
    @JsonProperty("ip")
    private String ip;

    /** 来源记录ID（download_log.id，历史迁移标记；新写入为 NULL） */
    @JsonProperty("sourceId")
    private Long sourceId;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
