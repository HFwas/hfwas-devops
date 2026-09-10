package com.hfwas.devops.container.dto;

import lombok.Data;

/**
 * VO for cross-registry image search results.
 * Aggregates image (repository) info from all registries into a flat structure
 * for the global image listing and search page.
 */
@Data
public class ImageSearchVO {
    private Long registryId;
    private String registryName;
    private String registryUrl;
    private String projectName;
    private String repoName;
    private Integer artifactCount;
    private Long pullCount;
    private String updateTime;
}