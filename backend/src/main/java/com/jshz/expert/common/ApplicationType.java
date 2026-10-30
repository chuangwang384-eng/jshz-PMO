package com.jshz.expert.common;

/**
 * 通用申请类型枚举（我的申请改版）。
 * 一期仅专家审批流两类；未来新增业务（如请假 LEAVE、报销 EXPENSE）= 加枚举 + 提交处理器 + 前端表单。
 */
public enum ApplicationType {

    /** 专家信息修改申请（原 expert_change_request.request_type=0） */
    EXPERT_MODIFY,

    /** 新增专家申请（原 expert_change_request.request_type=1） */
    EXPERT_CREATE,

    /** 专家合同审批（项目管理模块结算合同；登记后发起，通过后合同生效） */
    CONTRACT;

    /**
     * 解析类型编码；空或未知值按 EXPERT_MODIFY 兜底（历史数据兼容）。
     * 注意：合同审批走显式 submitContractApproval/approve 分支，不会走到此兜底。
     */
    public static ApplicationType parse(String code) {
        if (code == null || code.isBlank()) {
            return EXPERT_MODIFY;
        }
        try {
            return ApplicationType.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            return EXPERT_MODIFY;
        }
    }

    /**
     * 兼容旧前端 requestType 数值：1=新增专家，0=修改申请/合同审批。
     */
    public int requestType() {
        return this == EXPERT_CREATE ? 1 : 0;
    }

    /**
     * 展示文案（我的申请 / 审批管理列表通用）。
     */
    public String text() {
        if (this == EXPERT_CREATE) {
            return "新增专家申请";
        }
        if (this == CONTRACT) {
            return "专家合同审批";
        }
        return "专家信息修改申请";
    }
}
