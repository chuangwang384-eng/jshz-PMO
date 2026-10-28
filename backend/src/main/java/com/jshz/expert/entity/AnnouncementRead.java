package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告已读记录实体，对应表 announcement_read。
 * 唯一联合键 (announcement_id, user_id) 保证标记已读幂等。
 */
@Data
@TableName("announcement_read")
public class AnnouncementRead {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    @JsonProperty("announcementId")
    private Long announcementId;

    @JsonProperty("userId")
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("readTime")
    private LocalDateTime readTime;
}
