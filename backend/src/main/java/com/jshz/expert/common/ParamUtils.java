package com.jshz.expert.common;

import com.jshz.expert.security.AuthUser;

/**
 * Controller/Service 请求参数转换工具（全项目唯一实现，防多处副本语义漂移）。
 * 统一语义：解析失败返回 null（不抛异常），由调用方按业务语义报错；
 * str：trim 后空串归一为 null。
 */
public final class ParamUtils {

    private ParamUtils() {
    }

    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析 Long（REQ-20261102 加固）：null / 空串 / "null" / 非数字 → 返回 fallback，不抛异常。
     * 业务必需字段解析为 null 时应抛明确 BusinessException，禁止 NumberFormatException/500。
     */
    public static Long parseLong(Object value, Long fallback) {
        Long v = toLong(value);
        return v == null ? fallback : v;
    }

    public static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 分页参数钳制（2026-11-24）：把外部传入的 page/size 收敛到 [min, max]。
     *
     * <p>背景：MyBatis-Plus {@code PaginationInnerInterceptor} 在 {@code size < 0} 且未配置
     * {@code maxLimit} 时不会构造分页 SQL，等于"返回全表"；{@code page < 1} 也会被强制为 1。
     * 这是框架语义而非业务语义，调用方必须先钳制再传给 {@code new Page<>(page, size)}。</p>
     */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Long userIdOf(AuthUser authUser) {
        return authUser == null ? null : authUser.getUserId();
    }

    public static String usernameOf(AuthUser authUser) {
        return authUser == null ? null : authUser.getUsername();
    }    /**
     * LIKE 关键字转义（2026-11-26 项目板块审查 P3-3）：转义 \ % _，防通配符误匹配。
     * MySQL LIKE 默认以反斜杠为转义符，参数化传参加前缀即可生效（无需 ESCAPE 子句）。
     * 空值原样返回 null。
     */
    public static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

