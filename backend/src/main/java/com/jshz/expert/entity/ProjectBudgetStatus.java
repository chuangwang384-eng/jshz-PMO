package com.jshz.expert.entity;

/**
 * 项目会议预算单状态（2026-11-26 审查收口，AGENTS.md 17.7 成对登记）。
 * 权威定义在后端本枚举；前端展示副本见 frontend/src/utils/dict.js BUDGET_STATUS_TEXT，
 * 新增/改值须同一次提交成对修改两侧。当前取值：DRAFT 草稿 / CONFIRMED 已确认。
 */
public enum ProjectBudgetStatus {

    DRAFT,
    CONFIRMED;

    /** 落库/比较用的字符串值（与 project_budget.status 列一致）。 */
    public String code() {
        return name();
    }
}
