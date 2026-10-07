package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.error.HelmChartConflictException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps version conflicts to HTTP 409 ahead of the generic advice that answers with HTTP 200.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HelmChartExceptionHandler {

    @ExceptionHandler(HelmChartConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResult<Void> conflict(HelmChartConflictException ex) {
        return BaseResult.failed(ex.getCode(), ex.getMessage());
    }
}
