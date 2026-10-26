package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商行价条目实体（supplier_price_item）：服务项+价格+单位+备注。
 */
@Data
@TableName("supplier_price_item")
public class SupplierPriceItem {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    @JsonProperty("supplierId")
    private Long supplierId;

    /** 服务项 */
    @JsonProperty("itemName")
    private String itemName;

    /** 价格 */
    @JsonProperty("price")
    private BigDecimal price;

    /** 单位 */
    @JsonProperty("unit")
    private String unit;

    /** 备注 */
    @JsonProperty("remark")
    private String remark;

    @JsonProperty("sort")
    private Integer sort;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
