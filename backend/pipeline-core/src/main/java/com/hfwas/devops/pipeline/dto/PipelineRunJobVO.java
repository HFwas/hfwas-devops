package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PipelineRunJobVO {
    private Long id;
    private Long jobId;
    private String stageName;
    private String jobName;
    private String kind;
    private String command;
    private String status;
    private String logText;
    private String podName;
    private String namespace;
    private String[] containers;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
