package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HelmChartSummaryVO {
    private Long repositoryId;
    private String chartName;
    private String latestVersion;
    private int versionCount;
    private String description;
    private String appVersion;
    private LocalDateTime updatedAt;
}
