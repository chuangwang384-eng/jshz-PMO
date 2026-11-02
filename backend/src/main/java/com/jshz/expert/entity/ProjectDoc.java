package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分阶段资料（上传无类型/大小限制；删除软删留痕）。
 * 对应数据库表 project_doc。
 */
@Data
@TableName("project_doc")
public class ProjectDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 project.id */
    private Long projectId;

    /** 挂载阶段 project_phase.id（分阶段挂载） */
    private Long phaseId;

    /** 文件名 */
    private String fileName;

    /** 文件路径（本地存储，同 expert 照片模式） */
    private String filePath;

    /** 上传人用户名 */
    private String uploadBy;

    /** 软删标记：1已删除 */
    private Integer isDeleted;

    /** 删除人用户名（留痕） */
    private String deletedBy;

    /** 删除时间（留痕） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
