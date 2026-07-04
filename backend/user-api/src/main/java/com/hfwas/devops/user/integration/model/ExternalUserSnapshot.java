package com.hfwas.devops.user.integration.model;

import lombok.Builder;
import lombok.Data;

/**
 * Normalized user record from an external identity source.
 */
@Data
@Builder
public class ExternalUserSnapshot {
    /** Stable external identifier (LDAP entryUUID, uid, etc.). */
    private String externalId;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private boolean enabled;
}
