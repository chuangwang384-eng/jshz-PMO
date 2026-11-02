package com.jshz.expert.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信通讯录同步配置（2026-10-27 评审通过）。
 *
 * <p>注入方式与 JWT_SECRET 一致：值来自环境变量 WECOM_CORP_ID / WECOM_CONTACT_SECRET /
 * AUTH_LOGIN_MODE，禁止写入仓库；sync-cron 与 sync-cron-enabled 为普通配置可放 yml。</p>
 *
 * <p>fail-fast：sync-cron-enabled=true 但 corp-id/secret 为空时启动失败，
 * 防止运维打开定时任务后发现凭证缺失。</p>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "expert.wecom")
public class WecomProperties {

    /** 企业 CorpID（管理后台-我的企业） */
    private String corpId = "";

    /** 通讯录同步助手·只读 secret（管理工具-通讯录同步助手） */
    private String contactSecret = "";

    /** 每日定时同步 cron（默认 02:30） */
    private String syncCron = "0 30 2 * * ?";

    /** 定时同步开关：上线稳定后由运维置 true */
    private boolean syncCronEnabled = false;

    /** 登录标识模式：NAME / COMPAT / MOBILE */
    private String loginMode = "NAME";

    /** 企微成员详情逐人调用节流间隔毫秒（几百人规模在频率限制内） */
    private long userGetIntervalMs = 100L;

    /** token 提前刷新余量（秒） */
    private long tokenRefreshAheadSeconds = 300L;

    public boolean hasCredential() {
        return corpId != null && !corpId.isBlank()
                && contactSecret != null && !contactSecret.isBlank();
    }

    @PostConstruct
    public void validate() {
        if (syncCronEnabled && !hasCredential()) {
            throw new IllegalStateException(
                    "expert.wecom.sync-cron-enabled=true 但 WECOM_CORP_ID / WECOM_CONTACT_SECRET 未注入，启动中止（fail-fast）");
        }
        String mode = loginMode == null ? "NAME" : loginMode.trim().toUpperCase();
        if (!"NAME".equals(mode) && !"COMPAT".equals(mode) && !"MOBILE".equals(mode)) {
            throw new IllegalStateException("AUTH_LOGIN_MODE 非法：" + loginMode + "（仅支持 NAME/COMPAT/MOBILE）");
        }
    }
}
