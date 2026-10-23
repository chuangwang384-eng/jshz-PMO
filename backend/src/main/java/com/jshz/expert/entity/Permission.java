package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点实体（RBAC 阶段1，对应数据库表 permission）。
 * code 可重复（菜单/接口可共用同一码）；UNIQUE(method,path)；
 * 菜单行 method=MENU、path=权限码。
 */
@Data
@TableName("permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 权限码（如 expert:manage / expert:export） */
    @JsonProperty("code")
    private String code;

    /** 权限名称 */
    @JsonProperty("name")
    private String name;

    /** 类型：1=菜单 2=接口 */
    @JsonProperty("type")
    private Integer type;

    /** HTTP 方法；菜单行为 MENU */
    @JsonProperty("method")
    private String method;

    /** 接口路径（Ant 模式）；菜单行为权限码 */
    @JsonProperty("path")
    private String path;

    /** 父菜单权限码（预留） */
    @JsonProperty("parentCode")
    private String parentCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;
}
