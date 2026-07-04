package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class UserProfile {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String role;
    private Integer enabled;
    /** Login tenant context (from JWT), not platform home tenant */
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    /** Tenants this user has joined (platform user list) */
    private List<String> tenantNames;
    /** local | ldap | oauth2 */
    private String authSource;
    private String connectorName;
}
