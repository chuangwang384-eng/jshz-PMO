package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目参会专家（2026-11-09 流程重塑：日程解析候选 + 人工确认正式记录 + 手动补录）。
 * role：CHAIR 大会主席 / HOST 大会主持 / SPEAKER 大会讲者 / DISCUSSANT 讨论嘉宾。
 * confirmed=0 为 AI 解析候选（必须人工确认），1 为正式记录；expert_id NULL=库外专家。
 */
@Data
@TableName("project_speaker")
public class ProjectSpeaker {

    public static final String ROLE_CHAIR = "CHAIR";
    public static final String ROLE_HOST = "HOST";
    public static final String ROLE_SPEAKER = "SPEAKER";
    public static final String ROLE_DISCUSSANT = "DISCUSSANT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 来源日程；手动补录为 NULL */
    private Long scheduleId;

    /** 匹配到的专家档案 id；NULL=库外专家 */
    private Long expertId;

    private String expertName;

    /** CHAIR / HOST / SPEAKER / DISCUSSANT */
    private String role;

    /** 议题/段落 */
    private String segmentTitle;

    /** 时间段文本 */
    private String segmentTime;

    /** 0=候选，1=人工确认 */
    private Integer confirmed;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ---- 瞬态：匹配到的专家档案补充信息（非表字段） ----

    @TableField(exist = false)
    private String expertUnit;

    @TableField(exist = false)
    private String expertTitle;
}
