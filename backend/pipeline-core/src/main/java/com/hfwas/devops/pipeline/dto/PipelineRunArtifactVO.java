package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PipelineRunArtifactVO {
    private Long id;
    private Long runId;
    private Long jobId;
    private String artifactType;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private LocalDateTime createTime;
}