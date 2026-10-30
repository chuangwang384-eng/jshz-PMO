package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企微部门快照（2026-10-27 企微通讯录同步）。
 * 对应表 wecom_department；只存快照，不回写企微。
 */
@Data
@TableName("wecom_department")
public class WecomDepartment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企微部门id（根=1） */
    @JsonProperty("deptId")
    private Long deptId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("parentDeptId")
    private Long parentDeptId;

    @JsonProperty("sortOrder")
    private Integer sortOrder;

    /** 1正常 0企微已删除（保留快照） */
    @JsonProperty("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;
}
