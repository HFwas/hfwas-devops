package com.hfwas.devops.user.spi;

import java.util.List;

/**
 * Cross-module SPI for project-scoped access checks (API test, etc.).
 * Implemented by pm-core; consumers only depend on user-api.
 */
public interface ProjectAccessValidator {

    void assertAccess(Long projectId);

    List<Long> accessibleProjectIds();

    Long currentTenantId();
}
