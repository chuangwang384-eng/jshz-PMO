package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专家合同（登记信息+上传文件+发起审批；敏感字段回写 expert_info 并留痕）。
 * 对应数据库表 project_contract。
 */
@Data
@TableName("project_contract")
public class ProjectContract {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 专家 expert_info.id */
    private Long expertId;

    /** 专家姓名快照 */
    private String expertName;

    /** 身份证号快照（自动带出专家库，可改并回写 expert_info） */
    private String idCard;

    /** 银行卡号快照（自动带出专家库，可改并回写 expert_info） */
    private String bankCard;

    /** 合同金额 */
    private BigDecimal fee;

    /** 合同文件路径（上传后登记） */
    private String filePath;

    /** 状态：DRAFT 草稿 / PENDING 审批中 / APPROVED 已生效 / REJECTED 已驳回 */
    private String status;

    /** 审批单 id（复用 approval 模块 application.id） */
    private Long approvalRef;

    /** 登记人用户名 */
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
