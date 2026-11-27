package com.jshz.expert.common;

/**
 * 业务异常。由全局异常处理器统一转换为 {code, message} 响应。
 *
 * <p>契约说明：</p>
 * <ul>
 *   <li>继承 {@link RuntimeException}（非受检异常），可穿透 Service 边界而不污染方法签名，
 *       并保证事务回滚（Spring 默认对 RuntimeException 回滚）；</li>
 *   <li>由 {@code GlobalExceptionHandler#handleBusiness} 转为 {@code ApiResponse.error(code, message)}
 *       （HTTP 200 + body 非零 code），前端 request.js 按 {@code res.code !== 0} 判错并弹 message；</li>
 *   <li>message 面向用户展示，禁止包含 SQL/堆栈/secret 等内部细节。</li>
 * </ul>
 */
public class BusinessException extends RuntimeException {

    /** 序列化版本号（父类可序列化；显式声明避免反序列化兼容性告警） */
    private static final long serialVersionUID = 1L;

    /** 响应状态码：默认 400（参数/业务规则不满足）；须为非 0，前端以 code !== 0 判错 */
    private final int code;

    /** 简写构造：仅消息，状态码默认 400（参数/业务校验类错误） */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /** 全参构造：显式指定状态码（如 409 冲突、404 不存在），code 会原样进入响应 body */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 供全局异常处理器读取，映射为 ApiResponse.code */
    public int getCode() {
        return code;
    }
}
