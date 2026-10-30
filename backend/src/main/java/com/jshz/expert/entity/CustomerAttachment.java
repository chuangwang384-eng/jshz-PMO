package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户档案附件实体（多附件管理，无版本历史）。
 */
@Data
@TableName("customer_attachment")
public class CustomerAttachment {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户档案ID customer.id */
    private Long customerId;

    /** 原始文件名 */
    private String originalName;

    /** 存储文件名（客户附件库内） */
    private String storageName;

    /** 文件扩展名（小写） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 上传人 */
    private String uploadBy;

    /** 上传时间 */
    private LocalDateTime uploadTime;
}
