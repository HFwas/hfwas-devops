package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class HelmReleaseHistoryVO {
    private int revision;
    private String chartName;
    private String chartVersion;
    private String appVersion;
    private String status;
    private String description;
    private String updatedAt;
    private String valuesYaml;
}
