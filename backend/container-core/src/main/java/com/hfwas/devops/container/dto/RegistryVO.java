package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RegistryVO {
    private Long id;
    private Long tenantId;
    private String name;
    private String alias;
    private String type;
    private String url;
    private Boolean insecure;
    private String credentialUsername;
    private String source;
    private Long clusterId;
    private String status;
    private String lastError;
    private Map<String, String> labels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // credentialPassword is NEVER included in VO
}