package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商城市字典实体（supplier_city）：全国预置 + 录入自动沉淀，按省份分组。
 */
@Data
@TableName("supplier_city")
public class SupplierCity {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 省/直辖市/自治区/特别行政区 */
    @JsonProperty("province")
    private String province;

    /** 城市名 */
    @JsonProperty("name")
    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
