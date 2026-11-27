package com.jshz.expert.aspect;

import com.jshz.expert.annotation.OperationLog;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户操作日志 AOP 切面（用户操作日志改版）。
 * - 切点：@annotation 仅命中标注 {@link OperationLog} 的方法，现有手动埋点不标注 → 天然零重复；
 * - 记录时机：方法正常返回后记录（抛异常不记录），与现状手动埋点语义一致；
 * - 容错：切面内 try-catch 降级 + warn，日志写入失败不影响主业务；
 * - 操作人：优先从方法参数中找 @AuthenticationPrincipal AuthUser，找不到再从 SecurityContextHolder
 *   兜底取当前登录用户，仍找不到则记 null；
 * - targetId：注解指定了表达式（非空串）则按 SpEL 解析；为空串时（创建类操作）尝试从返回值
 *   ApiResponse.data 中提取 id 属性（Map 的 "id" 键或 getId() 方法）补记新生成的主键，提取不到记 null；
 * - IP：优先取当前 HTTP 请求（RequestContextHolder）由 OperationLogService 解析客户端 IP，
 *   非 Web 线程取不到时记 null。
 * 注意：Spring AOP 基于代理，同类内部 this 调用不会触发本切面；注解仅应标注在由外部（MVC）调用的
 * Controller 方法上。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    /** 注解模板是编译期常量，按模板缓存解析后的表达式，避免每次请求重复 parse */
    private static final ConcurrentHashMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    private final OperationLogService operationLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        // 先执行业务，仅成功路径记录
        Object result = pjp.proceed();
        try {
            AuthUser user = findAuthUser(pjp.getArgs());
            if (user == null) {
                user = findAuthUserFromSecurityContext();
            }
            String desc = safeResolve(operationLog.actionDesc(), pjp);
            String targetId = StringUtils.hasText(operationLog.targetId())
                    ? safeResolve(operationLog.targetId(), pjp)
                    : extractIdFromResult(result);
            operationLogService.record(
                    user == null ? null : user.getUserId(),
                    user == null ? null : user.getUsername(),
                    operationLog.actionType(), desc,
                    operationLog.targetType(), targetId, currentRequest());
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
     * 方法参数中找不到 AuthUser 时，从 SecurityContext 兜底取当前登录用户（匿名/非登录态返回 null）。
     */
    private AuthUser findAuthUserFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser) {
            return (AuthUser) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * 取当前 HTTP 请求用于记录客户端 IP；非 Web 线程（无请求上下文）返回 null。
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 注解未指定 targetId 时（创建类操作），尝试从返回值 ApiResponse.data 中提取主键：
     * 支持 Map 的 "id" 键或实体 getId() 方法；提取不到返回 null（日志降级，不抛给主业务）。
     */
    private String extractIdFromResult(Object result) {
        try {
            if (!(result instanceof ApiResponse<?> response) || response.getData() == null) {
                return null;
            }
            Object data = response.getData();
            if (data instanceof Map<?, ?> map) {
                Object id = map.get("id");
                return id == null ? null : String.valueOf(id);
            }
            Method getter = data.getClass().getMethod("getId");
            Object id = getter.invoke(data);
            return id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            log.warn("AOP 操作日志从返回值提取 targetId 失败（记 null）: err={}", e.getMessage());
            return null;
        }
    }

    /**
     * SpEL 模板解析：支持 #参数名 与 #p0/#p1 下标兜底；模板按字符串缓存解析结果；
     * 解析失败返回 null（日志降级，不抛给主业务）。
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
            Expression expression = EXPRESSION_CACHE.computeIfAbsent(template,
                    t -> SPEL_PARSER.parseExpression(t, ParserContext.TEMPLATE_EXPRESSION));
            return expression.getValue(ctx, String.class);
        } catch (Exception e) {
            log.warn("AOP 操作日志 SpEL 解析失败（降级为 null）: template={}, err={}", template, e.getMessage());
            return null;
        }
    }
}
