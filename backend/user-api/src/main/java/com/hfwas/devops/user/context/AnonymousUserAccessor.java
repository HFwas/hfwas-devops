package com.hfwas.devops.user.context;

import java.util.Optional;

/** Fallback when no security context is present (tests / internal jobs). */
public final class AnonymousUserAccessor implements CurrentUserAccessor {

    public static final AnonymousUserAccessor INSTANCE = new AnonymousUserAccessor();
    private static final Long SYSTEM_USER_ID = 111111L;

    private AnonymousUserAccessor() {
    }

    @Override
    public Optional<UserContext> current() {
        return Optional.of(UserContext.builder()
                .userId(SYSTEM_USER_ID)
                .username("system")
                .displayName("系统用户")
                .role("user")
                .tenantId(1L)
                .build());
    }
}
