package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class RegistryRepoVO {
    private Long id;
    private String projectName;
    private String name;
    private Integer artifactCount;
    private Long pullCount;
    private String creationTime;
    private String updateTime;
}