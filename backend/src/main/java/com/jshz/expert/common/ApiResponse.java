package com.jshz.expert.common;

import lombok.Data;

/**
 * 统一响应结构（设计文档接口约定：{code, message, data}）。
 * code = 0 表示成功，非 0 表示失败。
 */
@Data
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
