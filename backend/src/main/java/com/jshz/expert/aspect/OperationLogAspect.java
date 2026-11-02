package com.jshz.expert.aspect;

import com.jshz.expert.annotation.OperationLog;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户操作日志 AOP 切面（用户操作日志改版）。
 * - 切点：@annotation 仅命中标注 {@link OperationLog} 的方法，现有手动埋点不标注 → 天然零重复；
 * - 记录时机：方法正常返回后记录（抛异常不记录），与现状手动埋点语义一致；
 * - 容错：切面内 try-catch 降级 + warn，日志写入失败不影响主业务；
 * - 操作人：从方法参数中找 @AuthenticationPrincipal AuthUser，找不到则记 null（与现有部分调用一致）；
 * - IP：request 传 null（由 OperationLogService 内部跳过 IP）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final OperationLogService operationLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        // 先执行业务，仅成功路径记录
        Object result = pjp.proceed();
        try {
            AuthUser user = findAuthUser(pjp.getArgs());
            String desc = safeResolve(operationLog.actionDesc(), pjp);
            String targetId = StringUtils.hasText(operationLog.targetId())
                    ? safeResolve(operationLog.targetId(), pjp) : null;
            operationLogService.record(
                    user == null ? null : user.getUserId(),
                    user == null ? null : user.getUsername(),
                    operationLog.actionType(), desc,
                    operationLog.targetType(), targetId, null);
        } catch (Exception e) {
            log.warn("AOP 操作日志写入失败（不影响主业务）: actionType={}, err={}", operationLog.actionType(), e.getMessage());
        }
        return result;
    }

    /**
     * 遍历方法参数找 AuthUser（@AuthenticationPrincipal 注入）。
     */
    private AuthUser findAuthUser(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AuthUser) {
                return (AuthUser) arg;
            }
        }
        return null;
    }

    /**
     * SpEL 模板解析：支持 #参数名 与 #p0/#p1 下标兜底；解析失败返回 null（日志降级，不抛给主业务）。
     */
    private String safeResolve(String template, ProceedingJoinPoint pjp) {
        if (!StringUtils.hasText(template)) {
            return template;
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            Object[] args = pjp.getArgs();
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(signature.getMethod());
            for (int i = 0; i < args.length; i++) {
                String name = paramNames != null && i < paramNames.length ? paramNames[i] : null;
                ctx.setVariable("p" + i, args[i]);
                if (name != null && !name.isBlank()) {
                    ctx.setVariable(name, args[i]);
                }
            }
            return SPEL_PARSER.parseExpression(template, ParserContext.TEMPLATE_EXPRESSION)
                    .getValue(ctx, String.class);
        } catch (Exception e) {
            log.warn("AOP 操作日志 SpEL 解析失败（降级为 null）: template={}, err={}", template, e.getMessage());
            return null;
        }
    }
}
