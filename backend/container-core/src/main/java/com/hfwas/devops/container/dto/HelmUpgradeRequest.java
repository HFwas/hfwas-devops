package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class HelmUpgradeRequest {
    /** {@code keep} or {@code reset}. The client already resolved {@link #valuesYaml}. */
    private String valuesStrategy;
    private String chartRef;
    private Long artifactId;
    private String version;
    private String valuesYaml;
    private Boolean wait;
    private Boolean rollbackOnFailure;
}
