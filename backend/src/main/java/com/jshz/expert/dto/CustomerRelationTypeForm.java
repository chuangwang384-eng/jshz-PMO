package com.jshz.expert.dto;

import lombok.Data;

/**
 * 客户关系类型新增/编辑表单。
 * 新增仅需 name（编码由后端生成 custom_<时间戳>）；编辑可改 name、status（1启用 0停用）与 direction。
 * direction 取值：DIRECTED单向 / BIDIRECTED双向 / UNDIRECTED无向；新增缺省 DIRECTED；预置类型（builtin=1）方向禁改。
 */
@Data
public class CustomerRelationTypeForm {

    /** 类型名称（新增必填，编辑可选） */
    private String name;

    /** 状态：1启用 0停用（编辑时使用） */
    private Integer status;

    /** 关系方向：DIRECTED单向 / BIDIRECTED双向 / UNDIRECTED无向（新增缺省 DIRECTED；预置类型禁改） */
    private String direction;
}
