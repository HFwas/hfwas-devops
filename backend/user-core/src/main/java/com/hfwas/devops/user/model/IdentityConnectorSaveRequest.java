package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class IdentityConnectorSaveRequest {
    private Long id;
    private String name;
    private String type;
    /** Full or partial config JSON; blank bindPassword keeps existing value on update. */
    private String configJson;
    private Integer enabled;
    private Long defaultTenantId;
    private Integer autoCreateMember;
}
