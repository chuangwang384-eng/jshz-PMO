package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商档案实体（supplier）：全员创建维护，无审批。一供应商一城市。
 */
@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 供应商名称 */
    @JsonProperty("name")
    private String name;

    /** 类型 supplier_type.id */
    @JsonProperty("typeId")
    private Long typeId;

    /** 城市 supplier_city.id */
    @JsonProperty("cityId")
    private Long cityId;

    /** 地址 */
    @JsonProperty("address")
    private String address;

    /** 联系人 */
    @JsonProperty("contact")
    private String contact;

    /** 电话 */
    @JsonProperty("phone")
    private String phone;

    /** 微信 */
    @JsonProperty("wechat")
    private String wechat;

    /** 描述 */
    @JsonProperty("description")
    private String description;

    /** 图片文件名 JSON 数组（存储于供应商图库） */
    @JsonProperty("images")
    private String images;

    /** 创建人用户名 */
    @JsonProperty("createBy")
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    // ---------- 展示字段（非数据库列） ----------

    /** 类型名称 */
    @TableField(exist = false)
    @JsonProperty("typeName")
    private String typeName;

    /** 类型业务键 */
    @TableField(exist = false)
    @JsonProperty("typeBizKey")
    private String typeBizKey;

    /** 城市名 */
    @TableField(exist = false)
    @JsonProperty("cityName")
    private String cityName;

    /** 省份 */
    @TableField(exist = false)
    @JsonProperty("province")
    private String province;

    /** 图片文件名列表（images 解析） */
    @TableField(exist = false)
    @JsonProperty("imageList")
    private List<String> imageList;

    /** 平均评分（1-5，一位小数；无评价为 null） */
    @TableField(exist = false)
    @JsonProperty("avgStars")
    private Double avgStars;

    /** 评价数 */
    @TableField(exist = false)
    @JsonProperty("reviewCount")
    private Long reviewCount;
}
