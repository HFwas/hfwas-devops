package com.hfwas.devops.apitest;

import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.error.ResultCode;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 测试环境下的全局异常处理器
 * <p>
 * 模拟 server 模块的 ExceptionAdvice，仅包含 api-test-core 模块需要的异常处理逻辑。
 * 避免在测试中加载 server 模块的完整上下文（含 Spring Security 依赖）。
 *
 * @author hfwas
 */
@Slf4j
@RestControllerAdvice
public class TestExceptionAdvice {

    @ExceptionHandler(ApiTestException.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleApiTestException(ApiTestException e) {
        log.warn("[ApiTest] {}", e.getMessage());
        return BaseResult.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        return BaseResult.failed(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("Missing request parameter: {}", e.getMessage());
        return BaseResult.failed(ResultCode.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public BaseResult<Void> handleException(Exception e) {
        log.error(e.getMessage(), e);
        return BaseResult.failed(ResultCode.INTERNAL_ERROR);
    }
}