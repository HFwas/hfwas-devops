package com.hfwas.devops.user.spi;

/**
 * Validates whether a user may access data under a tenant (membership / admin).
 */
public interface TenantAccessValidator {

    void assertAccess(Long userId, String role, Long tenantId);
}
