package com.jshz.expert.dto;

import lombok.Data;

import java.util.List;

/**
 * 客户档案新增/编辑表单。
 */
@Data
public class CustomerForm {

    /** 客户姓名（必填） */
    private String name;

    /** 工作单位 */
    private String workUnit;

    /** 工作职位 */
    private String workPosition;

    /** 工作职级 */
    private String workLevel;

    /** 现任职务 */
    private String currentDuty;

    /** 客户喜好（文本） */
    private String preferences;

    /** 备注 */
    private String remark;

    /** 新建档案时批量关联已有客户（仅新增时生效；编辑忽略） */
    private List<RelationItem> relations;

    /**
     * 新建档案时关联已有客户的关系项。
     */
    @Data
    public static class RelationItem {

        /** 关联的已有客户ID（必填） */
        private Long targetCustomerId;

        /** 关系类型ID customer_relation_type.id（必填） */
        private Long relationTypeId;

        /** 方向（仅单向类型有意义）：NEW_TO_TARGET=新客户→已有客户（默认）；TARGET_TO_NEW=已有客户→新客户；双向/无向类型可省略 */
        private String direction;

        /** 关系说明 */
        private String description;
    }
}
