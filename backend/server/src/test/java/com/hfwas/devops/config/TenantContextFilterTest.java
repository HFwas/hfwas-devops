package com.hfwas.devops.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.core.exception.ApiErrorWriter;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.security.AuthUserPrincipal;
import com.hfwas.devops.user.security.TenantContextService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantContextFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiErrorWriter apiErrorWriter = new ApiErrorWriter(objectMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonMemberReturns403() throws Exception {
        TenantContextService service = mock(TenantContextService.class);
        when(service.resolveAndValidate(any(), eq(7L), eq("user"), isNull()))
                .thenThrow(new BizException(ResultCode.TENANT_FORBIDDEN));

        MockHttpServletResponse response = invoke(service);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(readCode(response)).isEqualTo(ResultCode.TENANT_FORBIDDEN.getCode());
    }

    @Test
    void invalidTenantHeaderReturns400() throws Exception {
        TenantContextService service = mock(TenantContextService.class);
        when(service.resolveAndValidate(any(), eq(7L), eq("user"), isNull()))
                .thenThrow(new IllegalArgumentException("无效的租户 ID"));

        MockHttpServletResponse response = invoke(service);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(readCode(response)).isEqualTo(ResultCode.TENANT_ID_INVALID.getCode());
    }

    private MockHttpServletResponse invoke(TenantContextService service) throws Exception {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("user");
        user.setRole("user");
        user.setEnabled(1);
        AuthUserPrincipal principal = new AuthUserPrincipal(user, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        TenantContextFilter filter = new TenantContextFilter(service, apiErrorWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pm/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    private int readCode(MockHttpServletResponse response) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        return body.get("code").asInt();
    }
}
