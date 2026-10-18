package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.error.BizException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 测试专用异常映射，复刻 server 模块 ExceptionAdvice 的约定：
 * 业务异常返回 HTTP 200，错误码放在 BaseResult.code。
 */
@RestControllerAdvice
class PipelineTestExceptionAdvice {

    @ExceptionHandler(BizException.class)
    ResponseEntity<BaseResult<Void>> handleBiz(BizException ex) {
        return ResponseEntity.ok(BaseResult.failed(ex.getCode(), ex.getMessage()));
    }
}
