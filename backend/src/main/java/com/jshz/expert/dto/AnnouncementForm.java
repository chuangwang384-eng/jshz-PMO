package com.jshz.expert.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告创建/编辑表单。
 * content 为富文本 HTML；expireTime 为空表示永久有效；status 仅 ONLINE/OFFLINE。
 */
@Data
public class AnnouncementForm {

    private String title;

    private String content;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private Integer isTop;

    private String status;
}
