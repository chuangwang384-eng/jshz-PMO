package com.jshz.expert.common;

import java.util.List;

/**
 * 项目生命周期阶段名常量（2026-11-26 P2-15：收敛散落字面量）。
 *
 * <p>背景：阶段名此前在 Service / Show / Settlement / Closeout 等多处以中文字面量硬编码，
 * 阶段字典一旦改名，业务判定会**静默失效**（不报错、不抛异常，只是不再匹配）。
 * 本类把阶段名收敛为唯一真源，并配合
 * {@code ProjectPhaseDictionaryCheck} 在启动期校验阶段字典中确实存在这些阶段名，
 * 使改名变成「启动即报 ERROR」的显式故障，而不是运行期的无声退化。
 *
 * <p>阶段名与 {@code project_phase.name} 对应；若后续改用稳定的阶段编码（phase_code），
 * 应在本类统一替换，调用方无需改动。
 */
public final class ProjectPhaseNames {

    /** 会议预算制作（报价单阶段）。 */
    public static final String BUDGET_MAKING = "会议预算制作";

    /** 会前财务资料上传（合规立项申请表阶段）。 */
    public static final String FINANCE_UPLOAD = "会前财务资料上传";

    /** 日程上传。 */
    public static final String SCHEDULE_UPLOAD = "日程上传";

    /** 串场制作（PPT / 离线网页幻灯片阶段）。 */
    public static final String SHOW_MAKING = "串场制作";

    /** 结算资料上传。 */
    public static final String SETTLEMENT_UPLOAD = "结算资料上传";

    /** 会议费用结算及项目结项。 */
    public static final String CLOSEOUT = "会议费用结算及项目结项";

    /** 默认生命周期的阶段顺序（用于启动期校验字典完整性）。 */
    public static final List<String> LIFECYCLE = List.of(
            BUDGET_MAKING, FINANCE_UPLOAD, SCHEDULE_UPLOAD, SHOW_MAKING, SETTLEMENT_UPLOAD, CLOSEOUT);

    private ProjectPhaseNames() {}
}
