package com.hfwas.devops.container.config;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Argo shares one cluster across tenants. Until per-tenant namespaces exist,
 * every /argo/** endpoint is platform-admin only.
 */
@Component
@RequiredArgsConstructor
public class ArgoAdminInterceptor implements HandlerInterceptor {

    private final CurrentUserAccessor currentUserAccessor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!currentUserAccessor.isAdmin()) {
            throw new BizException(ResultCode.ADMIN_REQUIRED);
        }
        return true;
    }
}
