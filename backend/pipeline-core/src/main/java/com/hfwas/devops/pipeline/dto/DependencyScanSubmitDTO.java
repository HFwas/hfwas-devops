package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class DependencyScanSubmitDTO {
    private String repoUrl;
    private String gitRef;
    private Long credentialId;
    private String stack;          // JAVA_MAVEN / NODE / GO / PYTHON
    private String runtimeVersion;
}