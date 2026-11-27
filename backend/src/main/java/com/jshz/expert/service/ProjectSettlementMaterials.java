package com.jshz.expert.service;

import com.jshz.expert.entity.ProjectDoc;

import java.util.List;
import java.util.Objects;

/** Settlement upload slots and their stable archive paths. */
final class ProjectSettlementMaterials {
    static final String PHASE = com.jshz.expert.common.ProjectPhaseNames.SETTLEMENT_UPLOAD;
    static final String PREFIX = ProjectStorageService.SETTLEMENT_FILE_PREFIX;

    static final String PACKAGE_PREFIX = PREFIX + "package_";

    record Slot(String code, String group, String name, String description, String projectType,
                String suggestedUploader, String suggestedReviewer, String deadline,
                boolean required, boolean versioned, boolean allowsNotApplicable) {
        String prefix() { return PREFIX + code + "_"; }
    }

    private static final List<Slot> SLOTS = List.of(
            new Slot("U01", "供应商费用核对", "酒店发票", "酒店各项费用核对无误后，告知酒店开具的发票", "", "", "", "", true, false, true),
            new Slot("U02", "供应商费用核对", "酒店合规支持材料", "与酒店发票对应的合规支持材料", "", "", "合规专员", "", true, false, true),
            new Slot("U03", "供应商费用核对", "其他供应商发票及明细单", "其他费用的发票及明细单等合规材料", "", "", "", "客户确认后统一开具", true, false, true),
            new Slot("U04", "客户垫付费用及票据", "客户垫付费用明细", "客户自行垫付费用的金额明细", "", "", "", "", false, false, false),
            new Slot("U05", "客户垫付费用及票据", "客户垫付发票", "客户自行垫付费用的发票", "", "", "", "", false, false, false),
            new Slot("U06", "客户垫付费用及票据", "高铁票据", "我司垫付高铁费用的票据", "", "", "", "", false, false, false),
            // 2026-11-25 负责人要求：劳务费用资料包拆为两个细项，劳务协议必须上传（沿用 U07 编码与既有文件）
            new Slot("U07", "劳务费用资料包", "劳务协议", "与劳务人员或合作平台签署的劳务协议，必须上传", "", "", "合规专员", "7个工作日内", true, false, true),
            new Slot("U07B", "劳务费用资料包", "会议资料", "劳务费用对应的会议资料（签到、照片、会议通知等），按需上传", "", "", "合规专员", "7个工作日内", false, false, true),
            new Slot("U08", "成本单制作", "成本单", "当场会议所有开支（含客户垫付费用、劳务费用），按公司成本单模板在线填表，提交后自动生成 Excel", "", "", "", "", true, true, false),
            new Slot("U09", "结算单制作", "预算单", "作为结算单制作依据的预算单", "", "", "", "", true, true, false),
            new Slot("U10", "结算单制作", "真实结算单", "调整数量、增加新增项目后制作的真实结算单", "", "", "", "经我司支付费用版本：会议结束后2个工作日内", true, true, false),
            new Slot("U11", "结算单确认与转化", "客户确认版结算单", "客户确认无误的结算单（或确认记录）", "", "", "", "", true, true, false),
            new Slot("U12", "结算单确认与转化", "合规转化后结算单", "客户确认后进行合规转化的结算单", "", "", "合规专员", "", true, true, false),
            new Slot("U13", "合规资料整理", "合规资料整体包", "按合作平台要求整体打包的合规资料", "", "合规专员", "", "", true, true, false),
            new Slot("U14", "财务提交内审", "盖章结算单", "盖章后的结算单", "DIRECT_PAY", "财务", "", "", true, true, false),
            new Slot("U15", "财务提交内审", "开具的发票", "向客户开具的发票", "DIRECT_PAY", "财务", "", "", true, false, false),
            new Slot("U16", "财务提交内审", "邮寄凭证", "邮寄客户的快递单号或回执", "DIRECT_PAY", "财务", "", "", true, false, false),
            new Slot("U17", "财务提交内审", "平台审核提交凭证", "合规专员向平台提交审核的凭证", "PLATFORM", "合规专员", "", "", true, false, false),
            new Slot("U18", "财务提交内审", "补充资料", "平台后续审核要求补交的资料", "PLATFORM", "合规专员", "", "当天提交", false, true, false),
            new Slot("U19", "财务提交内审", "内部报销单据", "用于进入内部报销流程、结转费用的单据", "", "", "", "", true, false, false)
    );

    static List<Slot> applicable(String projectType) {
        return SLOTS.stream()
                .filter(slot -> slot.projectType().isEmpty() || Objects.equals(slot.projectType(), projectType))
                .toList();
    }

    static boolean manages(String path) {
        return path != null && path.startsWith(PREFIX) && !path.startsWith(PACKAGE_PREFIX);
    }

    static boolean isReason(ProjectDoc doc) {
        return manages(doc.getFilePath()) && doc.getFilePath().split("_", 5)[4].startsWith("na_");
    }

    static String batch(ProjectDoc doc) {
        return doc.getFilePath().split("_", 5)[3];
    }

    private ProjectSettlementMaterials() {}
}
