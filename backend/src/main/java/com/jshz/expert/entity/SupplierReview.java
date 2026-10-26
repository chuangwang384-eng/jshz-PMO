package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商评价实体（supplier_review）：1-5 星 + 文字，提交即显示姓名与时间。
 */
@Data
@TableName("supplier_review")
public class SupplierReview {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    @JsonProperty("supplierId")
    private Long supplierId;

    /** 评价人姓名 */
    @JsonProperty("username")
    private String username;

    /** 1-5 星 */
    @JsonProperty("stars")
    private Integer stars;

    /** 评价内容 */
    @JsonProperty("content")
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
