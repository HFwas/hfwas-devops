package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.List;

@Data
public class HelmChartDetailVO {
    private Long repositoryId;
    private String repositoryName;
    private String chartName;
    private String description;
    private String version;
    private String appVersion;
    private String chartRef;
    private Long artifactId;
    private List<String> keywords;
    private String readme;
    private List<HelmChartVersionVO> versions;
}
