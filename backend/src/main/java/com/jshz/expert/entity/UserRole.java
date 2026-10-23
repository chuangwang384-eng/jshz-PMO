package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体（RBAC 阶段1，对应数据库表 user_role）。
 */
@Data
@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 用户ID（sys_user.id） */
    @JsonProperty("userId")
    private Long userId;

    /** 角色ID（role.id） */
    @JsonProperty("roleId")
    private Long roleId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
