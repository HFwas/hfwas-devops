package com.hfwas.devops.common.core.exception;

import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.core.requestid.RequestIdHolder;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequiredArgsConstructor
public class ExceptionAdvice {

    private final ApiErrorWriter apiErrorWriter;

    @ExceptionHandler(BizException.class)
    public ResponseEntity<BaseResult<Void>> handleBizException(BizException e) {
        log.warn("[{}] {}", e.getCode(), e.getMessage());
        return respond(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ApiTestException.class)
    public ResponseEntity<BaseResult<Void>> handleApiTestException(ApiTestException e) {
        log.warn("[ApiTest] {}", e.getMessage());
        return respond(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResult<Void>> handleIllegalArgument(IllegalArgumentException e) {
        BizException biz = LegacyErrorCodeResolver.resolve(e);
        log.warn("[{}] {}", biz.getCode(), biz.getMessage());
        return respond(biz.getCode(), biz.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<BaseResult<Void>> handleIllegalState(IllegalStateException e) {
        BizException biz = LegacyErrorCodeResolver.resolve(e);
        log.warn("[{}] {}", biz.getCode(), biz.getMessage());
        return respond(biz.getCode(), biz.getMessage());
    }

    private ResponseEntity<BaseResult<Void>> respond(int code, String message) {
        return ResponseEntity.status(apiErrorWriter.resolveHttpStatus(code))
                .body(withRequestId(BaseResult.failed(code, message)));
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public BaseResult<Void> handleAuthentication(AuthenticationException e) {
        log.warn("Authentication failed: {}", e.getMessage());
        return withRequestId(BaseResult.failed(ResultCode.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public BaseResult<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return withRequestId(BaseResult.failed(ResultCode.FORBIDDEN));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleValidation(Exception e) {
        log.warn("Validation failed: {}", e.getMessage());
        return withRequestId(BaseResult.failed(ResultCode.BAD_REQUEST));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public BaseResult<Void> handleNotFound(NoResourceFoundException e) {
        return withRequestId(BaseResult.failed(ResultCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleException(Exception e) {
        log.error("Unhandled exception [requestId={}]", RequestIdHolder.get(), e);
        return withRequestId(BaseResult.failed(ResultCode.INTERNAL_ERROR));
    }

    /** 注入 requestId 到响应体 */
    private static <T> BaseResult<T> withRequestId(BaseResult<T> result) {
        result.setRequestId(RequestIdHolder.get());
        return result;
    }
}
