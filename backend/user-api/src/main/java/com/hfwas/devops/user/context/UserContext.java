package com.hfwas.devops.user.context;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable snapshot of the authenticated user for the current request.
 * {@code tenantId} reserved for future multi-tenant support.
 */
@Value
@Builder
public class UserContext {
    Long userId;
    String username;
    String displayName;
    String role;
    Long tenantId;
}
