package com.hfwas.devops.container.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeployResultVO {
    private Long clusterId;
    private String namespace;
    private String deploymentName;
    private String pullSecretName;
    private String serviceName;
}