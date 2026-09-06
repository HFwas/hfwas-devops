package com.hfwas.devops.user.model;

import lombok.Data;

import java.util.Map;

@Data
public class KeycloakAuthEvent {
    private String id;
    private String type;
    private Long time;
    private String userId;
    private String ipAddress;
    private String error;
    private Map<String, String> details;
}
