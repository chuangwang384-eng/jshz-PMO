package com.jshz.expert.common;

import lombok.Data;

/**
 * 统一响应结构（设计文档接口约定：{code, message, data}）。
 * code = 0 表示成功，非 0 表示失败。
 *
 * <p>契约锚点（改动须前后端联动评估）：</p>
 * <ul>
 *   <li>前端 frontend/src/api/request.js 以 {@code res.code === 0} 判成功、{@code res.code === 401} 触发登录态处理；</li>
 *   <li>config/GlobalExceptionHandler 的所有异常处理器统一经 {@code ApiResponse.error(...)} 出口；</li>
 *   <li>全仓 37 个 Controller/切面引用；序列化输出 {@code {"code":..,"message":..,"data":..}}，data 为 null 也输出。</li>
 * </ul>
 */
@Data
public class ApiResponse<T> {

    /** 业务状态码：0=成功；非 0 复用 HTTP 语义码（400 参数错/401 未登录/403 无权限/409 冲突/500 服务器错误） */
    private int code;

    /** 提示消息：成功固定 "ok"；失败为面向用户的中文提示（兜底 500 不泄露内部细节） */
    private String message;

    /** 业务数据：失败时为 null；泛型随接口返回类型变化 */
    private T data;

    /** 无参构造：仅供反序列化/测试框架使用，业务代码请走下方工厂方法 */
    public ApiResponse() {
    }

    /** 全参构造：由工厂方法内部使用，不对外暴露直接构造 */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功响应（带数据）：code 固定 0，message 固定 "ok" */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /** 成功响应（无数据）：data 为 null，前端取 res.data 得 undefined/null */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    /** 失败响应（显式状态码）：code 传 0 会造成"错误被当成功"的矛盾，调用方须保证非 0 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 失败响应（简写）：未指定状态码时默认 500；4xx 业务错误请用 error(int, String) 或 BusinessException */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
