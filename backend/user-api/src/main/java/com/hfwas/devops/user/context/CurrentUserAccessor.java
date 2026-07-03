package com.hfwas.devops.user.context;

import java.util.Optional;

/**
 * Cross-module SPI: PM and other domains resolve the current user without depending on user-core.
 */
public interface CurrentUserAccessor {

    Optional<UserContext> current();

    default Long currentUserId() {
        return current().map(UserContext::getUserId).orElse(null);
    }

    default String currentDisplayName() {
        return current().map(UserContext::getDisplayName).orElse("匿名用户");
    }

    default String currentUsername() {
        return current().map(UserContext::getUsername).orElse("");
    }

    default boolean isAdmin() {
        return current().map(ctx -> "admin".equalsIgnoreCase(ctx.getRole())).orElse(false);
    }

    default boolean isAuthenticated() {
        return current().isPresent();
    }
}
