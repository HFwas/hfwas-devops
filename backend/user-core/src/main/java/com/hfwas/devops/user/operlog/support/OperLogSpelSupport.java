package com.hfwas.devops.user.operlog.support;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

public final class OperLogSpelSupport {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private OperLogSpelSupport() {
    }

    public static String eval(String spel, ProceedingJoinPoint pjp, Object result) {
        if (StringUtils.isBlank(spel)) {
            return autoDetectBizId(pjp, result);
        }
        StandardEvaluationContext ctx = buildContext(pjp, result);
        Object value = PARSER.parseExpression(spel).getValue(ctx);
        return value == null ? null : String.valueOf(value);
    }

    private static String autoDetectBizId(ProceedingJoinPoint pjp, Object result) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                if ("id".equals(names[i]) && args[i] != null) {
                    return String.valueOf(args[i]);
                }
            }
        }
        Object data = extractBaseResultData(result);
        if (data != null) {
            return String.valueOf(data);
        }
        return null;
    }

    private static StandardEvaluationContext buildContext(ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        ctx.setVariable("result", result);
        Object data = extractBaseResultData(result);
        ctx.setVariable("data", data);
        return ctx;
    }

    private static Object extractBaseResultData(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Method method = result.getClass().getMethod("getData");
            return method.invoke(result);
        } catch (Exception ignored) {
            return null;
        }
    }
}
