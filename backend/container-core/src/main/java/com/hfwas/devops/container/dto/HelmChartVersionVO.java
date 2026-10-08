package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HelmChartVersionVO {
    private Long artifactId;
    private String version;
    private String appVersion;
    private String chartRef;
    private String digest;
    private LocalDateTime createdAt;
}
