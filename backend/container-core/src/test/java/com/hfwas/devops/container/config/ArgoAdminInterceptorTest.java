package com.hfwas.devops.container.config;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArgoAdminInterceptorTest {

    @Test
    void nonAdminIsForbidden() {
        CurrentUserAccessor accessor = mock(CurrentUserAccessor.class);
        when(accessor.isAdmin()).thenReturn(false);
        ArgoAdminInterceptor interceptor = new ArgoAdminInterceptor(accessor);

        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest("GET", "/argo/workflows"),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ResultCode.ADMIN_REQUIRED.getCode());
    }

    @Test
    void adminIsAllowed() {
        CurrentUserAccessor accessor = mock(CurrentUserAccessor.class);
        when(accessor.isAdmin()).thenReturn(true);
        ArgoAdminInterceptor interceptor = new ArgoAdminInterceptor(accessor);

        assertThat(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/argo/workflows"),
                new MockHttpServletResponse(), new Object())).isTrue();
    }
}
