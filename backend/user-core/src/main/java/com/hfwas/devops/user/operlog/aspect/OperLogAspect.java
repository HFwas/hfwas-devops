package com.hfwas.devops.user.operlog.aspect;

import com.hfwas.devops.user.operlog.annotation.OperLog;
import com.hfwas.devops.user.operlog.model.OperLogEntry;
import com.hfwas.devops.user.operlog.service.OperLogService;
import com.hfwas.devops.user.operlog.support.OperLogSpelSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class OperLogAspect {

    private final OperLogService operLogService;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            String bizId = OperLogSpelSupport.eval(operLog.bizId(), pjp, result);
            operLogService.record(OperLogEntry.builder()
                    .module(operLog.module())
                    .action(operLog.action())
                    .bizType(operLog.bizType())
                    .bizId(bizId)
                    .summary(operLog.summary())
                    .status(OperLogService.STATUS_SUCCESS)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record oper log: {}", e.getMessage());
        }
        return result;
    }
}
