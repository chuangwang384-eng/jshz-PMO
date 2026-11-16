package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目协助人（多选，全员可选；2026-11-09 项目基本信息改版新增）。
 * 对应数据库表 project_assistant。
 */
@Data
@TableName("project_assistant")
public class ProjectAssistant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 协助人 sys_user.id */
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
