package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

@Data
public class PipelineSaveDTO {
    private Long id;
    private String name;
    private String repoUrl;
    private String gitRef;
    private Long credentialId;
    private List<PipelineStageDTO> stages;
}
