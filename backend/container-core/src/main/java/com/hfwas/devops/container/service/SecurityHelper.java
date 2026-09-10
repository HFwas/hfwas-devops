package com.hfwas.devops.container.service;

import com.hfwas.devops.user.security.AuthUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility to extract current user/tenant from Spring Security context.
 */
public final class SecurityHelper {

    private SecurityHelper() {}

    /**
     * Get the current tenant ID from the authenticated principal.
     * Returns null if not available (e.g. anonymous request).
     */
    public static Long currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            return principal.getLoginTenantId();
        }
        return null;
    }
}