package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("customer_employment")
public class CustomerEmployment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String workUnit;
    private String position;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String startMonth;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String endMonth;
}
