package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下载审计实体（正式版 V1.001，R5 下载留痕）。
 * 对应数据库表 download_log。
 */
@Data
@TableName("download_log")
public class DownloadLog {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 下载人 sys_user.id（可空） */
    @JsonProperty("userId")
    private Long userId;

    /** 下载人姓名 */
    @JsonProperty("username")
    private String username;

    /** 专家 id（全量下载时为 null） */
    @JsonProperty("expertId")
    private Long expertId;

    /** 专家姓名 */
    @JsonProperty("expertName")
    private String expertName;

    /** 知识文档ID（C4：download_type=knowledge_doc 时有值，其余为 null） */
    @JsonProperty("docId")
    private Long docId;

    /** 下载类型：selected_xlsx / selected_zip / full_xlsx / full_zip / md / template_xlsx（历史 selected_csv / full_csv 兼容） */
    @JsonProperty("downloadType")
    private String downloadType;

    /** 下载时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("downloadTime")
    private LocalDateTime downloadTime;

    /** 来源 IP */
    @JsonProperty("ip")
    private String ip;
}
