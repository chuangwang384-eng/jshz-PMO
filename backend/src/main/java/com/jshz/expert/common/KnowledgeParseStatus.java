package com.jshz.expert.common;

/**
 * 知识文档异步解析状态（P2-7）。
 * 与 knowledge_document.parse_status 列取值一致；NULL 表示历史数据或无需解析。
 */
public final class KnowledgeParseStatus {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private KnowledgeParseStatus() {
    }
}
