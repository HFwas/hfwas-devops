package com.hfwas.devops.common.core.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionAdviceTest {

    private final ExceptionAdvice advice =
            new ExceptionAdvice(new ApiErrorWriter(new ObjectMapper()));

    @Test
    void tenantForbiddenMapsTo403() {
        ResponseEntity<BaseResult<Void>> response =
                advice.handleBizException(new BizException(ResultCode.TENANT_FORBIDDEN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.TENANT_FORBIDDEN.getCode());
    }

    @Test
    void tenantContextMissingMapsTo401() {
        ResponseEntity<BaseResult<Void>> response =
                advice.handleBizException(new BizException(ResultCode.TENANT_CONTEXT_MISSING));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.TENANT_CONTEXT_MISSING.getCode());
    }

    @Test
    void projectAccessDeniedMapsTo403() {
        ResponseEntity<BaseResult<Void>> response =
                advice.handleBizException(new BizException(ResultCode.PROJECT_ACCESS_DENIED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.PROJECT_ACCESS_DENIED.getCode());
    }

    @Test
    void projectNotFoundMapsTo404() {
        ResponseEntity<BaseResult<Void>> response =
                advice.handleBizException(new BizException(ResultCode.PROJECT_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.PROJECT_NOT_FOUND.getCode());
    }

    @Test
    void internalErrorStays200() {
        ResponseEntity<BaseResult<Void>> response =
                advice.handleBizException(new BizException(ResultCode.INTERNAL_ERROR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void legacyInvalidTenantIdMapsTo400() {
        ResponseEntity<BaseResult<Void>> response =
                advice.handleIllegalArgument(new IllegalArgumentException("无效的租户 ID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.TENANT_ID_INVALID.getCode());
    }
}
