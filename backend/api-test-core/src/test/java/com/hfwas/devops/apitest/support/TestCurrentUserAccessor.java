package com.hfwas.devops.apitest.support;

import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;

import java.util.Optional;

/**
 * 测试用当前用户上下文，默认已登录用户 1 / 租户 1 / admin，可按用例切换。
 *
 * @author hfwas
 */
public class TestCurrentUserAccessor implements CurrentUserAccessor {

    public static final Long DEFAULT_USER_ID = 1L;
    public static final Long DEFAULT_TENANT_ID = 1L;

    private volatile UserContext context = buildUser(DEFAULT_USER_ID, DEFAULT_TENANT_ID, "admin");

    @Override
    public Optional<UserContext> current() {
        return Optional.ofNullable(context);
    }

    public void setUser(Long userId, Long tenantId, String role) {
        this.context = userId == null ? null : buildUser(userId, tenantId, role);
    }

    public void reset() {
        this.context = buildUser(DEFAULT_USER_ID, DEFAULT_TENANT_ID, "admin");
    }

    public void logout() {
        this.context = null;
    }

    private static UserContext buildUser(Long userId, Long tenantId, String role) {
        return UserContext.builder()
                .userId(userId)
                .username("tester")
                .displayName("测试用户")
                .role(role)
                .tenantId(tenantId)
                .build();
    }
}
