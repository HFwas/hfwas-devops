package com.hfwas.devops.apitest.common.security;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.spi.ProjectAccessValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * API 测试模块统一访问守卫。
 * <p>
 * 身份一律取当前认证上下文，不接受客户端传入的 userId；
 * 项目访问统一走 {@link ProjectAccessValidator}，缺失或越权 fail closed。
 *
 * @author hfwas
 */
@Component
@RequiredArgsConstructor
public class ApiTestAccessGuard {

    private final ProjectAccessValidator projectAccessValidator;
    private final CurrentUserAccessor currentUserAccessor;

    /**
     * 当前登录用户 ID；上下文缺失时拒绝。
     */
    public Long requireUserId() {
        Long userId = currentUserAccessor.currentUserId();
        if (userId == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return userId;
    }

    /**
     * 必填 projectId 的项目访问校验。
     */
    public void assertProject(Long projectId) {
        if (projectId == null) {
            throw new BizException(ResultCode.PROJECT_ID_REQUIRED);
        }
        projectAccessValidator.assertAccess(projectId);
    }

    /**
     * 当前租户下可见的项目 ID 列表；无租户上下文时拒绝。
     */
    public List<Long> accessibleProjectIds() {
        if (currentUserAccessor.currentTenantId() == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        List<Long> ids = projectAccessValidator.accessibleProjectIds();
        return ids != null ? ids : Collections.emptyList();
    }

    /**
     * 当前租户 ID；缺失时拒绝。
     */
    public Long requireTenantId() {
        Long tenantId = currentUserAccessor.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }
}
