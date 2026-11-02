package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商图片表（supplier_image）：图片登记 + 主图机制。
 * 每供应商至多一张主图（is_main=1），由生成列 main_key + 唯一索引
 * uk_supplier_main(supplier_id, main_key) 保证（is_main=0 时 main_key=NULL 不参与唯一约束）。
 */
@Data
@TableName("supplier_image")
public class SupplierImage {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 供应商ID supplier.id */
    @JsonProperty("supplierId")
    private Long supplierId;

    /** 磁盘文件名（supplier_<uuid>.<ext>） */
    @JsonProperty("fileName")
    private String fileName;

    /** 自定义显示名（≤50字符，空=显示原文件名） */
    @JsonProperty("name")
    private String name;

    /** 是否主图 1=是 0=否（每供应商至多一张主图） */
    @JsonProperty("isMain")
    private Integer isMain;

    /** 文件大小（字节） */
    @JsonProperty("fileSize")
    private Long fileSize;

    /** 排序（升序） */
    @JsonProperty("sortOrder")
    private Integer sortOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
