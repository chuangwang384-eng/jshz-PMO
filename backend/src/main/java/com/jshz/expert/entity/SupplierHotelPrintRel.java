package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 酒店-打印店绑定实体（supplier_hotel_print_rel，多对多）。
 * hotel_id 须为 biz_key=HOTEL 的供应商；print_shop_id 须为 biz_key=PRINT。
 */
@Data
@TableName("supplier_hotel_print_rel")
public class SupplierHotelPrintRel {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    @JsonProperty("hotelId")
    private Long hotelId;

    @JsonProperty("printShopId")
    private Long printShopId;

    @JsonProperty("createBy")
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
