package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户关系类型实体（预置枚举 + 维护人可扩展；可停用不可物理删除）。
 */
@Data
@TableName("customer_relation_type")
public class CustomerRelationType {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型编码（预置固定，自定义为 custom_<时间戳>） */
    private String code;

    /** 类型名称 */
    private String name;

    /** 1预置类型 0自定义类型 */
    private Integer builtin;

    /** 1启用 0停用 */
    private Integer status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
