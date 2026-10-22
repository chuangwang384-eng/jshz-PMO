package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专家信息实体（正式版 V1.001，18 字段英文列名 + 接口中文键）。
 * 对应数据库表 expert_info。
 * JSON 键为中文（与设计文档接口约定一致），数据库列名为英文下划线风格。
 */
@Data
@TableName("expert_info")
public class Expert {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 姓名 */
    @JsonProperty("姓名")
    private String name;

    /** 单位 */
    @JsonProperty("单位")
    private String unit;

    /** 科室 */
    @JsonProperty("科室")
    private String department;

    /** 职称 */
    @JsonProperty("职称")
    private String title;

    /** 研究方向 */
    @JsonProperty("研究方向")
    private String researchDirection;

    /** 擅长领域 */
    @JsonProperty("擅长领域")
    private String expertiseArea;

    /** 联系方式 */
    @JsonProperty("联系方式")
    private String contact;

    /** 简介 */
    @JsonProperty("简介")
    private String profile;

    /** 身份证号（查看默认脱敏，管理员全量） */
    @JsonProperty("身份证号")
    private String idCard;

    /** 银行卡号（查看默认脱敏，管理员全量） */
    @JsonProperty("银行卡号")
    private String bankCard;

    /** 证件照文件名（相对照片库目录） */
    @JsonProperty("照片")
    private String photo;

    /** 银行卡开户行 */
    @JsonProperty("银行卡开户行")
    private String bankBranch;

    /** 身份证正面图片文件名 */
    @JsonProperty("身份证正面")
    private String idCardFront;

    /** 身份证反面图片文件名 */
    @JsonProperty("身份证反面")
    private String idCardBack;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("创建时间")
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("修改时间")
    private LocalDateTime updateTime;

    /** 修改人id（登录用户名） */
    @JsonProperty("修改人id")
    private String updateBy;

    /** 下载总次数（非数据库列，仅列表展示用，R5 下载审计统计） */
    @TableField(exist = false)
    @JsonProperty("下载次数")
    private Long downloadCount;
}
