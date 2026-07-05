package com.hfwas.devops.user.spi;

public final class NoOpTenantAccessValidator implements TenantAccessValidator {

    public static final NoOpTenantAccessValidator INSTANCE = new NoOpTenantAccessValidator();

    private NoOpTenantAccessValidator() {
    }

    @Override
    public void assertAccess(Long userId, String role, Long tenantId) {
        throw new IllegalStateException("TenantAccessValidator is not configured");
    }
}
