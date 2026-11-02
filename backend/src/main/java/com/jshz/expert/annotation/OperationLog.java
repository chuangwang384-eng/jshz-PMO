package com.jshz.expert.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户操作日志注解（用户操作日志改版）。
 * 标注在 Controller 方法上，由 {@code OperationLogAspect} 在方法成功返回后统一记录操作日志。
 * SpEL 约定：
 * - {@code #body} 指方法第一个参数（@RequestBody 的 Map/实体），取参走 {@code #body.字段名}；
 * - 参数名取自局部变量表（-parameters 编译），无参数名时按下标 {@code #p0/#p1} 兜底；
 * - 无现成 ID 的方法可传空 targetId（记 null，与现有手动埋点一致）。
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
