package com.hfwas.devops.common.core.exception;

import com.hfwas.devops.common.core.base.BaseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn(e.getMessage());
        return BaseResult.failed(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public BaseResult<Void> handleException(Exception e) {
        log.error(e.getMessage(), e);
        return BaseResult.failed("服务器内部错误");
    }
}
