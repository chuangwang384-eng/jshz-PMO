package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目阶段变更历史（REQ-20261102 需求三）：记录推进/回退（含跨级），可追溯。
 * 对应数据库表 project_phase_history；action_type：ADVANCE 推进 / ROLLBACK 回退。
 */
@Data
@TableName("project_phase_history")
public class ProjectPhaseHistory {

    public static final String ACTION_ADVANCE = "ADVANCE";
    public static final String ACTION_ROLLBACK = "ROLLBACK";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 原阶段 */
    private Long fromPhaseId;

    /** 目标阶段 */
    private Long toPhaseId;

    /** 动作：ADVANCE / ROLLBACK */
    private String actionType;

    /** 原因（回退必填） */
    private String reason;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人用户名 */
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
