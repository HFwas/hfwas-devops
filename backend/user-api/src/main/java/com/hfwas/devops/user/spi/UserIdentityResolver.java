package com.hfwas.devops.user.spi;

/**
 * Resolves user ids from login or display names (e.g. PM Excel import).
 */
public interface UserIdentityResolver {

    Long resolveByUsername(String username);

    Long resolveByDisplayName(String displayName);

    /** Login name for export; falls back to display name or id string. */
    default String resolveUsername(Long userId) {
        return userId == null ? null : String.valueOf(userId);
    }
}
