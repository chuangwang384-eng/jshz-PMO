package com.jshz.expert.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户操作日志注解（用户操作日志改版）。
 * 标注在 Controller 方法上，由 {@code OperationLogAspect} 在方法成功返回后统一记录操作日志。
 * SpEL 约定（按方法真实参数名绑定，依赖 -parameters 编译保留参数名，本项目默认已开启）：
 * - 取请求体参数直接用参数名，如 {@code #body.字段名}（惯例将第一个 @RequestBody 参数命名为 body）；
 * - 编译产物无参数名时按下标 {@code #p0/#p1} 兜底；
 * - targetId 为空串时（创建类操作），切面会尝试从返回值 ApiResponse.data 中提取 id 属性补记
 *   新生成的主键（支持 Map 的 "id" 键或实体 getId() 方法），提取不到记 null。
 * 注意：Spring AOP 基于代理，同类内部 this 调用不会触发切面，本注解仅用于 Controller 方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作类型，对应 OperationLogService.ACTION_* 常量 */
    String actionType();

    /** 描述模板，支持 SpEL，如 "新增供应商：#{#body.name}" */
    String actionDesc();

    /** 目标对象类型，如 SUPPLIER */
    String targetType();

    /** 目标 ID 的 SpEL 表达式，如 "#body.id"；为空则记 null */
    String targetId() default "";
}
