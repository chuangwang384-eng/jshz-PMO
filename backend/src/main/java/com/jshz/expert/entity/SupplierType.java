package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商类型字典实体（supplier_type）。
 * biz_key 为固定业务键（HOTEL/PRINT/...）：管理员可改名称，绑定关系按 biz_key 识别类型。
 */
@Data
@TableName("supplier_type")
public class SupplierType {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 固定业务键（内置 6 类锁定；自定义类型 NULL） */
    @JsonProperty("bizKey")
    private String bizKey;

    /** 类型名称（管理员可改） */
    @JsonProperty("name")
    private String name;

    /** 排序（小在前） */
    @JsonProperty("sort")
    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;
}
