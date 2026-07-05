package com.hfwas.devops.common.core.exception;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class ExceptionAdvice {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleBizException(BizException e) {
        log.warn("[{}] {}", e.getCode(), e.getMessage());
        return BaseResult.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        BizException biz = LegacyErrorCodeResolver.resolve(e);
        log.warn("[{}] {}", biz.getCode(), biz.getMessage());
        return BaseResult.failed(biz.getCode(), biz.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleIllegalState(IllegalStateException e) {
        BizException biz = LegacyErrorCodeResolver.resolve(e);
        log.warn("[{}] {}", biz.getCode(), biz.getMessage());
        return BaseResult.failed(biz.getCode(), biz.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public BaseResult<Void> handleAuthentication(AuthenticationException e) {
        log.warn("Authentication failed: {}", e.getMessage());
        return BaseResult.failed(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public BaseResult<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return BaseResult.failed(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleValidation(Exception e) {
        log.warn("Validation failed: {}", e.getMessage());
        return BaseResult.failed(ResultCode.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public BaseResult<Void> handleNotFound(NoResourceFoundException e) {
        return BaseResult.failed(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleException(Exception e) {
        log.error(e.getMessage(), e);
        return BaseResult.failed(ResultCode.INTERNAL_ERROR);
    }
}
