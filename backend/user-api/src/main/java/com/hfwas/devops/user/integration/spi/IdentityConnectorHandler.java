package com.hfwas.devops.user.integration.spi;

import com.hfwas.devops.user.integration.model.ConnectorSyncResult;
import com.hfwas.devops.user.integration.model.ConnectorTestResult;
import com.hfwas.devops.user.integration.model.ExternalUserSnapshot;

import java.util.List;

/**
 * Pluggable external identity connector (LDAP today; OAuth2/SAML later).
 * Implementations are registered via Spring and discovered by {@code IdentityConnectorRegistry}.
 */
public interface IdentityConnectorHandler {

    /** Connector type code, e.g. {@code ldap}. */
    String type();

    /** Human-readable label for admin UI. */
    String typeLabel();

    /** Validate config JSON before save. */
    void validateConfig(String configJson);

    /** Test connectivity without persisting. */
    ConnectorTestResult testConnection(String configJson);

    /** Pull users from external directory. */
    List<ExternalUserSnapshot> fetchUsers(String configJson);

    /** Optional post-sync hook; default no-op. */
    default void afterSync(String configJson, ConnectorSyncResult result) {
    }
}
