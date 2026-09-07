package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

@Data
public class PipelineStageDTO {
    private Long id;
    private String name;
    private Integer sortOrder;
    private List<PipelineJobDTO> jobs;
}
