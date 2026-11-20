package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专家资料文件表（expert_document，2026-11-20）。
 * 医生证等资质资料登记行；文件实体存 {STORAGE_ROOT}\专家资料库，
 * 经 /api/files-doc/{storedName} 登录后访问。禁止在其他服务复制本表的存储命名规则。
 */
@Data
@TableName("expert_document")
public class ExpertDocument {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 专家 id（expert.id） */
    @JsonProperty("expertId")
    private Long expertId;

    /** 原始文件名（展示用） */
    @JsonProperty("fileName")
    private String fileName;

    /** 物理存储文件名（expertdoc_<expertId>_<ts>.<ext>） */
    @JsonProperty("storedName")
    private String storedName;

    /** 字节数 */
    @JsonProperty("fileSize")
    private Long fileSize;

    /** 上传人用户 id */
    @JsonProperty("createdBy")
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
