package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ClusterVO {
    private Long id;
    private Long tenantId;
    private String name;
    private String alias;
    private String provider;
    private String version;
    private String mode;
    private String status;
    private Map<String, String> labels;
    private Integer nodeCount;
    private Integer podCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // kubeconfig is NEVER included in VO
}