package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HelmChartArtifactVO {
    private Long id;
    private Long repositoryId;
    private String repositoryName;
    private String chartName;
    private String version;
    private String digest;
    private Long sizeBytes;
    private String chartRef;
    private String description;
    private String appVersion;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
