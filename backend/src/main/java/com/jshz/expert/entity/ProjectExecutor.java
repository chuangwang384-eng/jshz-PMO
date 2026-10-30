package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目执行人（多人；创建时默认带入创建人）。
 * 对应数据库表 project_executor。
 */
@Data
@TableName("project_executor")
public class ProjectExecutor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 执行人 sys_user.id */
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
