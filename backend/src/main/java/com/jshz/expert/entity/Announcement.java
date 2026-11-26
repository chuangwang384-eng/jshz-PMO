package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告实体，对应表 announcement。
 * 富文本 content 入库前经 XssCleanUtil 白名单清洗；read/expired/readCount 等为非表字段。
 */
@Data
@TableName("announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("content")
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("effectiveTime")
    private LocalDateTime effectiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("expireTime")
    private LocalDateTime expireTime;

    /** 置顶标记：0 否 / 1 是 */
    @JsonProperty("isTop")
    private Integer isTop;

    /** 置顶组内排序值（需求四 2026-11-26）：值越小越靠前；仅 isTop=1 时有意义；0=未排序（置顶时自动取最小值-1，即排最前） */
    @JsonProperty("topSort")
    private Integer topSort;

    /** ONLINE 上架 / OFFLINE 下架 */
    @JsonProperty("status")
    private String status;

    @JsonProperty("creatorId")
    private Long creatorId;

    @JsonProperty("creatorName")
    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    /** 当前用户是否已读（非表字段） */
    @TableField(exist = false)
    @JsonProperty("read")
    private Boolean read;

    /** 是否已过期（非表字段） */
    @TableField(exist = false)
    @JsonProperty("expired")
    private Boolean expired;

    /** 已读人数（管理视角，非表字段） */
    @TableField(exist = false)
    @JsonProperty("readCount")
    private Long readCount;

    /** 总启用人数（管理视角，非表字段） */
    @TableField(exist = false)
    @JsonProperty("totalCount")
    private Long totalCount;

    /** 当前用户是否可管理（非表字段） */
    @TableField(exist = false)
    @JsonProperty("canManage")
    private Boolean canManage;
}
