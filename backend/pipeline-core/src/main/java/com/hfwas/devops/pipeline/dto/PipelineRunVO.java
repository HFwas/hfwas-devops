package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PipelineRunVO {
    private Long id;
    private Long pipelineId;
    private String pipelineName;
    private String status;
    private String trigger;
    private String gitRef;
    private String commitSha;
    private String triggeredByName;
    private String stack;
    private String runtimeVersion;
    private String toolVersion;
    private String image;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<PipelineRunJobVO> jobs;
}
