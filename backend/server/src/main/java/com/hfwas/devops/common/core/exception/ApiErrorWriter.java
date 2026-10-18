package com.hfwas.devops.common.core.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.core.requestid.RequestIdHolder;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ErrorCode;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.container.error.ContainerErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus httpStatus, ErrorCode errorCode) throws IOException {
        write(response, httpStatus, errorCode.getCode(), errorCode.getMessage());
    }

    public void write(HttpServletResponse response, HttpStatus httpStatus, ErrorCode errorCode, String message)
            throws IOException {
        write(response, httpStatus, errorCode.getCode(), message);
    }

    public void write(HttpServletResponse response, HttpStatus httpStatus, BizException ex) throws IOException {
        write(response, httpStatus, ex.getCode(), ex.getMessage());
    }

    public void write(HttpServletResponse response, HttpStatus httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // 设置响应头，方便调用方检索日志
        String requestId = RequestIdHolder.get();
        if (requestId != null) {
            response.setHeader(RequestIdHolder.HEADER_NAME, requestId);
        }

        // 响应体注入 requestId
        BaseResult<Object> result = BaseResult.failed(code, message);
        result.setRequestId(requestId);

        objectMapper.writeValue(response.getWriter(), result);
    }

    public HttpStatus resolveHttpStatus(int code) {
        if (code == ResultCode.UNAUTHORIZED.getCode()
                || code == ResultCode.TENANT_CONTEXT_MISSING.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ResultCode.TENANT_ID_INVALID.getCode()
                || code == ResultCode.INVALID_TENANT_ROLE.getCode()
                || code == ResultCode.PROJECT_ID_REQUIRED.getCode()
                || code == ResultCode.PROJECT_TYPE_CODE_REQUIRED.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ResultCode.FORBIDDEN.getCode()
                || code == ResultCode.TENANT_FORBIDDEN.getCode()
                || code == ResultCode.ADMIN_REQUIRED.getCode()
                || code == ResultCode.PLATFORM_ADMIN_REQUIRED.getCode()
                || code == ResultCode.PROJECT_ACCESS_DENIED.getCode()
                || code == ContainerErrorCode.CLUSTER_FORBIDDEN.getCode()
                || code == ContainerErrorCode.REGISTRY_FORBIDDEN.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ResultCode.NOT_FOUND.getCode()
                || code == ResultCode.PROJECT_NOT_FOUND.getCode()
                || code == ResultCode.WORK_ITEM_NOT_FOUND.getCode()
                || code == ResultCode.FIELD_NOT_FOUND.getCode()
                || code == ResultCode.USER_NOT_FOUND.getCode()
                || code == ResultCode.TENANT_NOT_FOUND.getCode()
                || code == ContainerErrorCode.CLUSTER_NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.OK;
    }
}
