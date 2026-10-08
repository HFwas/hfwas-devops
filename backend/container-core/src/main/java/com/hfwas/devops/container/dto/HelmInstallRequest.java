package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class HelmInstallRequest {
    private String name;
    private String chartRef;
    private Long artifactId;
    private String valuesYaml;
    private Boolean createNamespace;
    private Boolean wait;
}
