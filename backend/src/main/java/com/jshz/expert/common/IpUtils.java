package com.jshz.expert.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * HTTP 客户端 IP 提取工具（统一维护，防两处实现漂移）。
 * 顺序：X-Real-IP → X-Forwarded-For 末段 → RemoteAddr。
 *
 * <p>取值可信度依据（2026-11-27 核实 D:\nginx\conf\conf.d\jshz-pmo.conf）：</p>
 * <ul>
 *   <li>X-Real-IP 由 Nginx 覆盖设置为 $remote_addr（TCP 对端），客户端无法伪造，故最优先；</li>
 *   <li>X-Forwarded-For 为 $proxy_add_x_forwarded_for 追加模式：客户端自带值保留在首段、
 *       可被任意伪造，Nginx 把真实地址追加在末段——因此只能取末段，禁止取首段；</li>
 *   <li>无代理直连时两者都缺失，落 RemoteAddr。</li>
 * </ul>
 * 调用方：OperationLogService（操作日志）、DownloadAuditService（下载审计）；request 为 null 时返回 null。
 */
public final class IpUtils {

    /** 工具类，禁止实例化 */
    private IpUtils() {
    }

    /**
     * 从当前 HTTP 请求解析客户端 IP，保证返回值不含 "unknown" 等占位脏值：
     * 优先不可伪造的 X-Real-IP；其次 X-Forwarded-For 从右向左取最后一个有效段；
     * 均无时取 TCP 对端地址；request 为 null（非 Web 线程）返回 null。
     */
    public static String clientIp(HttpServletRequest request) {
        // 非 Web 线程（如异步任务）传入 null：直接返回 null，由调用方落库为空
        if (request == null) {
            return null;
        }
        // 第一优先级：X-Real-IP。Nginx 覆盖设置为 $remote_addr，客户端伪造无效
        String ip = request.getHeader("X-Real-IP");
        // 有值且不等于占位符 "unknown" 才采信
        if (isTrusted(ip)) {
            return ip.trim();
        }
        // 第二优先级：X-Forwarded-For（追加模式，形如 "<伪造值>, <真实IP>"）
        String forwarded = request.getHeader("X-Forwarded-For");
        // 整头无有效内容时跳过分段解析
        if (StringUtils.hasText(forwarded)) {
            // 从右向左扫描：末段由本层可信代理追加，越靠左越可能是客户端伪造值
            String[] segments = forwarded.split(",");
            for (int i = segments.length - 1; i >= 0; i--) {
                // 逐段清洗：跳过空段（如 ", , 1.2.3.4"）与 "unknown" 占位段
                String segment = segments[i].trim();
                // 找到第一个可信段即返回（顺带规避旧实现首段为空串/"unknown" 的脏值问题）
                if (isTrusted(segment)) {
                    return segment;
                }
            }
        }
        // 兜底：无代理直连后端时的 TCP 对端地址（本机调试常为 IPv6 回环形式）
        return request.getRemoteAddr();
    }

    /**
     * 判断一个 IP 候选值是否可信：非空白且不等于代理占位符 "unknown"（大小写不敏感）。
     */
    private static boolean isTrusted(String value) {
        // 有实际内容且不是 "unknown" 占位符才算有效候选
        return StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value.trim());
    }
}
