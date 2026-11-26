package com.jshz.expert.service;

import java.util.List;

final class ProjectCloseout {
    static final String PHASE = "会议费用结算及项目结项";
    static final String PREFIX = "project_closeout_";

    record Item(String code, String label, String evidence) {
        String prefix() {
            return PREFIX + code + "_";
        }
    }

    private static final Item LABOR = new Item("labor", "劳务费用全部结转完毕", "结转记录或回执");
    private static final Item EXECUTION = new Item("execution", "会务执行费用结算至我司账户", "银行回单");
    private static final Item DIRECT = new Item("direct", "会议整体费用结算至我司账户", "银行回单");

    static List<Item> items(String projectType) {
        if ("PLATFORM".equals(projectType)) return List.of(LABOR, EXECUTION);
        if ("DIRECT_PAY".equals(projectType)) return List.of(DIRECT);
        return List.of(new Item("general", "通用完结证明", "项目完结证明文件"));
    }

    private ProjectCloseout() {}
}
