package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PipelineVO {
    private Long id;
    private String name;
    private String repoUrl;
    private String gitRef;
    private Long credentialId;
    private LocalDateTime updateTime;
    private Long lastRunId;
    private String lastRunStatus;
    private LocalDateTime lastRunTime;
    private List<PipelineStageDTO> stages;
}
