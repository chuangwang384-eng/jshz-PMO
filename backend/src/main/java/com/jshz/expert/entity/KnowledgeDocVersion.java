package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档版本历史实体（知识星球）。
 * 对应数据库表 knowledge_doc_version。
 * 字段与列名一致（camelCase ↔ underscore 由 MyBatis-Plus 自动映射）。
 */
@Data
@TableName("knowledge_doc_version")
public class KnowledgeDocVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档ID(knowledge_document.id) */
    private Long docId;

    /** 版本号（1,2,3...） */
    private Integer version;

    /** 文件原始文件名 */
    private String fileName;

    /** 文件存储文件名（知识文档库内） */
    private String filePath;

    /** 文件扩展名（小写） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 该版本上传人 */
    private String uploadBy;

    /** 该版本上传时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;
}
