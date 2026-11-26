package com.jshz.expert.entity;

/**
 * 参会专家角色权威枚举（2026-11-26 项目板块审查 P2-2 收口）。
 * 与 project_speaker.role 列（varchar(20)，DDL 注释即本枚举的持久化口径）一一对应；
 * 前端唯一副本为 frontend/src/utils/showDeck.js（ROLE_ENUM_LABEL），两侧成对修改（AGENTS 17.7）。
 */
public enum SpeakerRole {
    /** 大会主席 */
    CHAIR,
    /** 大会主持 */
    HOST,
    /** 大会讲者 */
    SPEAKER,
    /** 讨论嘉宾 */
    DISCUSSANT;

    /** 解析库内字符串；未知值返回 null（历史数据防御，不抛异常）。 */
    public static SpeakerRole parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
