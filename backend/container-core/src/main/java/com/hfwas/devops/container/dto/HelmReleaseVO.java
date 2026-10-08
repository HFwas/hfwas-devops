package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HelmReleaseVO {
    private String clusterId;
    private String namespace;
    private String name;
    private String chartName;
    private String chartVersion;
    private String appVersion;
    private String chartRef;
    private Long repositoryId;
    private Long artifactId;
    private String status;
    private int revision;
    private String valuesYaml;
    private String notes;
    private String manifest;
    private List<HelmReleaseResourceVO> resources = new ArrayList<>();
    private List<HelmReleaseHistoryVO> history = new ArrayList<>();
    private String updatedAt;
}
