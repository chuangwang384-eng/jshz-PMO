package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档收藏实体（C3，对应表 knowledge_favorite）。
 * doc_id + user_id 唯一约束（一人一文档一条收藏）。
 */
@Data
@TableName("knowledge_favorite")
public class KnowledgeFavorite {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Long id;

    /** 文档ID knowledge_document.id */
    @JsonProperty("docId")
    private Long docId;

    /** 用户ID sys_user.id */
    @JsonProperty("userId")
    private Long userId;

    /** 收藏时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
