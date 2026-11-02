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

    public static Long userIdOf(AuthUser authUser) {
        return authUser == null ? null : authUser.getUserId();
    }

    public static String usernameOf(AuthUser authUser) {
        return authUser == null ? null : authUser.getUsername();
    }
}
