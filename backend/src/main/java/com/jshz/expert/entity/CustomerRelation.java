package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户关系实体（有向关系：from -> to，如"介绍关系"）。
 */
@Data
@TableName("customer_relation")
public class CustomerRelation {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关系起点客户ID customer.id */
    private Long fromCustomerId;

    /** 关系终点客户ID customer.id */
    private Long toCustomerId;

    /** 关系类型ID customer_relation_type.id */
    private Long relationTypeId;

    /** 关系说明 */
    private String description;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
