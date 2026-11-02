package com.jshz.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点（GET /api/admin/wecom/departments）。
 * 部门节点带人数；展开见成员卡。
 */
@Data
public class DepartmentNodeVo {

    @JsonProperty("deptId")
    private Long deptId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("parentDeptId")
    private Long parentDeptId;

    @JsonProperty("sortOrder")
    private Integer sortOrder;

    /** 1正常 0企微已删除 */
    @JsonProperty("status")
    private Integer status;

    /** 本部门直接成员数（按主部门归属计数，避免一人多部门重复） */
    @JsonProperty("memberCount")
    private Integer memberCount = 0;

    @JsonProperty("members")
    private List<MemberItem> members = new ArrayList<>();

    @JsonProperty("children")
    private List<DepartmentNodeVo> children = new ArrayList<>();

    @Data
    public static class MemberItem {
        @JsonProperty("userId")
        private Long userId;
        @JsonProperty("username")
        private String username;
        @JsonProperty("mobileMasked")
        private String mobileMasked;
        @JsonProperty("isMain")
        private Boolean isMain;
        @JsonProperty("status")
        private Integer status;
    }
}
