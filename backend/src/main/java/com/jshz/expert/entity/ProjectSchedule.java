package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目日程文件（2026-11-09 流程重塑第一阶段「日程上传」）。
 * 一个项目可有多份历史，is_current=1 为当前生效份；raw_result 保存原始解析 JSON 供回溯。
 */
@Data
@TableName("project_schedule")
public class ProjectSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 id */
    private Long projectId;

    /** 原始文件名（仅展示，不参与存储路径） */
    private String fileName;

    /** 项目资料库随机文件名 */
    private String filePath;

    /** 字节 */
    private Long fileSize;

    private String mimeType;

    /** PENDING / PARSING / DONE / FAILED */
    private String parseStatus;

    private String parseMessage;

    /** 原始解析输出 JSON（回溯用） */
    private String rawResult;

    /** 1=当前生效份 */
    private Integer isCurrent;

    private Long uploadedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
