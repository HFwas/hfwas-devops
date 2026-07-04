package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class LdapConnectorConfig {
    private String url;
    private String baseDn;
    private String bindDn;
    private String bindPassword;
    /** LDAP filter, e.g. (&(objectClass=person)(uid=*)) */
    private String userFilter = "(&(objectClass=person)(uid=*))";
    private String usernameAttribute = "uid";
    private String displayNameAttribute = "cn";
    private String emailAttribute = "mail";
    private String phoneAttribute = "telephoneNumber";
    /** Optional attribute for stable external id; falls back to username. */
    private String externalIdAttribute = "entryUUID";
}
