package com.jshz.expert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 助手知识条目（2026-11-16）。管理员「AI 助手知识库」页维护；
 * 链接与步骤为人工维护的确定内容，模型只做意图匹配，不生成操作路径。
 */
@Data
@TableName("ai_assistant_entry")
public class AiAssistantEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String entryKey;

    private String title;

    /** 问法样例，每行一条 */
    private String questions;

    private String answer;

    /** 站内跳转深链 */
    private String linkUrl;

    private String linkLabel;

    /** 操作步骤，每行一步 */
    private String steps;

    /** 所需权限码；NULL=全员可见 */
    private String permCode;

    private Integer sort;

    private Integer isActive;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
