package com.jshz.expert.dto;

import lombok.Data;

/**
 * 客户关系建立表单（有向：from -> to，如"介绍关系"）。
 */
@Data
public class CustomerRelationForm {

    /** 关系起点客户ID（必填） */
    private Long fromCustomerId;

    /** 关系终点客户ID（必填） */
    private Long toCustomerId;

    /** 关系类型ID customer_relation_type.id（必填） */
    private Long relationTypeId;

    /** 关系说明 */
    private String description;

    /** 关系方向（可选，兼容字段）：DIRECTED/BIDIRECTED/UNDIRECTED；实际方向以关系类型 direction 为准，本字段仅用于显式声明 */
    private String direction;
}
