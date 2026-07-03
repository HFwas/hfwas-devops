package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class LoginRequest {
    /** Tenant slug, defaults to {@code default} */
    private String tenantCode;
    private String username;
    private String password;
}
