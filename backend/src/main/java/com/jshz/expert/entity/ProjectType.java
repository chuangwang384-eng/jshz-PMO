package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目类型字典（管理员可增删改，种子：展会/会议/论坛）。
 * 对应数据库表 project_type。
 */
@Data
@TableName("project_type")
public class ProjectType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型名称 */
    private String name;

    /** 排序（小在前） */
    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
