package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档主表实体（知识星球）。
 * 对应数据库表 knowledge_document。
 * 字段与列名一致（camelCase ↔ underscore 由 MyBatis-Plus 自动映射）。
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类：meeting-execution/association-compliance/enterprise-compliance/customer-preferences */
    private String category;

    /** 文档标题 */
    private String title;

    /** 当前版本号（从1递增） */
    private Integer version;

    /** 当前文件原始文件名 */
    private String fileName;

    /** 当前文件存储文件名（知识文档库内） */
    private String filePath;

    /** 文件扩展名（小写） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 首次上传人 */
    private String uploadBy;

    /** 最近更新人 */
    private String updateBy;

    /** 1上架 0下架 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
