package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class PipelineJobDTO {
    private Long id;
    private String name;
    private String kind;
    private String command;
    private Integer sortOrder;
}
