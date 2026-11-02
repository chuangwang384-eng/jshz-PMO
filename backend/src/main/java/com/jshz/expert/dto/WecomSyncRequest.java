package com.jshz.expert.dto;

import lombok.Data;

/**
 * 触发企微同步请求（dryRun=true 仅试算，不落业务数据）。
 */
@Data
public class WecomSyncRequest {
    /** true=DryRun 试算（只写变更清单）；false/缺省=实跑 */
    private Boolean dryRun;
}
