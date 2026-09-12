package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DependencyScanVO {
    private Long pipelineId;
    private String pipelineName;
    private Long runId;
    private String repoUrl;
    private String gitRef;
    private String status;
    private String commitSha;
    private String triggeredByName;
    private Integer componentCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
}