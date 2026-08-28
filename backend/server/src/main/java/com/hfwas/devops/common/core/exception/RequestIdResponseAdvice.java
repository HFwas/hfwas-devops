package com.hfwas.devops.common.core.exception;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.core.requestid.RequestIdHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应拦截器，在序列化前自动注入 requestId。
 * 确保所有 BaseResult 响应都携带 requestId 字段。
 */
@RestControllerAdvice
public class RequestIdResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType, Class selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof BaseResult) {
            BaseResult<?> result = (BaseResult<?>) body;
            if (result.getRequestId() == null) {
                result.setRequestId(RequestIdHolder.get());
            }
        }
        return body;
    }
}