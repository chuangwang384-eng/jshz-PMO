package com.jshz.expert.dto;

import lombok.Data;

/**
 * 管理员人工指认绑定请求（重名/改名场景兜底）。
 */
@Data
public class WecomBindRequest {
    /** 企微 userid（通讯录同步 secret 拉取到的成员 ID） */
    private String wecomUserId;
}
