package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企微成员-部门归属（2026-10-27 企微通讯录同步）。
 * 对应表 wecom_user_department；企微一人可属多部门。
 */
@Data
@TableName("wecom_user_department")
public class WecomUserDepartment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** sys_user.id */
    @JsonProperty("userId")
    private Long userId;

    /** 企微部门id */
    @JsonProperty("deptId")
    private Long deptId;

    /** 1主部门 0非主部门 */
    @JsonProperty("isMain")
    private Integer isMain;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
