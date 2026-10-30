package com.jshz.expert.dto;

import lombok.Data;

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
}
