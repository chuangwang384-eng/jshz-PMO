package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目-专家关联（全局一份，不分阶段；移出留痕 operation_log）。
 * 对应数据库表 project_expert。
 */
@Data
@TableName("project_expert")
public class ProjectExpert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 专家 expert_info.id */
    private Long expertId;

    /** 角色/分工（主持人/演讲嘉宾/评审专家等） */
    private String role;

    /** 劳务费（结算阶段登记） */
    private BigDecimal fee;

    /** 合同状态快照：NONE/DRAFT/PENDING/APPROVED/REJECTED */
    private String contractStatus;

    /** 关联人用户名 */
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
