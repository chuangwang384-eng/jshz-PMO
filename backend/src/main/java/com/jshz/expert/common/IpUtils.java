package com.jshz.expert.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * HTTP 客户端 IP 提取工具（统一维护，防两处实现漂移）。
 * 顺序：X-Forwarded-For 首段 → X-Real-IP → RemoteAddr。
 */
public final class IpUtils {

    private IpUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
