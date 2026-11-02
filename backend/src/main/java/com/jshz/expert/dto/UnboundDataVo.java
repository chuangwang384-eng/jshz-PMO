package com.jshz.expert.dto;

import com.jshz.expert.entity.SysUser;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 未绑定清单（GET /api/admin/wecom/unbound）：
 * 系统未绑定存量用户 + 企微侧未匹配成员（来自最近一次同步的 UNBOUND 变更项）。
 */
@Data
public class UnboundDataVo {

    @JsonProperty("systemUsers")
    private List<SysUser> systemUsers = new ArrayList<>();

    @JsonProperty("wecomMembers")
    private List<WecomMemberItem> wecomMembers = new ArrayList<>();

    @Data
    public static class WecomMemberItem {
        @JsonProperty("userId")
        private String userId;
        @JsonProperty("name")
        private String name;
        @JsonProperty("mobileMasked")
        private String mobileMasked;
        @JsonProperty("reason")
        private String reason;
    }
}
