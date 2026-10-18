package com.hfwas.devops.common.core.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.container.error.ContainerErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorWriterTest {

    private final ApiErrorWriter writer = new ApiErrorWriter(new ObjectMapper());

    @Test
    void tenantContextMissingMapsTo401() {
        assertThat(writer.resolveHttpStatus(ResultCode.TENANT_CONTEXT_MISSING.getCode()))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tenantForbiddenMapsTo403() {
        assertThat(writer.resolveHttpStatus(ResultCode.TENANT_FORBIDDEN.getCode()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void projectAccessDeniedMapsTo403() {
        assertThat(writer.resolveHttpStatus(ResultCode.PROJECT_ACCESS_DENIED.getCode()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void containerForbiddenMapsTo403() {
        assertThat(writer.resolveHttpStatus(ContainerErrorCode.CLUSTER_FORBIDDEN.getCode()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(writer.resolveHttpStatus(ContainerErrorCode.REGISTRY_FORBIDDEN.getCode()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void projectNotFoundMapsTo404() {
        assertThat(writer.resolveHttpStatus(ResultCode.PROJECT_NOT_FOUND.getCode()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(writer.resolveHttpStatus(ResultCode.WORK_ITEM_NOT_FOUND.getCode()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(writer.resolveHttpStatus(ContainerErrorCode.CLUSTER_NOT_FOUND.getCode()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidTenantHeaderMapsTo400() {
        assertThat(writer.resolveHttpStatus(ResultCode.TENANT_ID_INVALID.getCode()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(writer.resolveHttpStatus(ResultCode.INVALID_TENANT_ROLE.getCode()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingProjectScopeMapsTo400() {
        assertThat(writer.resolveHttpStatus(ResultCode.PROJECT_ID_REQUIRED.getCode()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(writer.resolveHttpStatus(ResultCode.PROJECT_TYPE_CODE_REQUIRED.getCode()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fieldNotFoundMapsTo404() {
        assertThat(writer.resolveHttpStatus(ResultCode.FIELD_NOT_FOUND.getCode()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void otherBusinessErrorsStay200() {
        assertThat(writer.resolveHttpStatus(ResultCode.INTERNAL_ERROR.getCode()))
                .isEqualTo(HttpStatus.OK);
        assertThat(writer.resolveHttpStatus(ResultCode.BAD_REQUEST.getCode()))
                .isEqualTo(HttpStatus.OK);
    }
}
