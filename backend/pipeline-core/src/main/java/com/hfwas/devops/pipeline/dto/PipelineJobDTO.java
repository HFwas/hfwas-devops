package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class PipelineJobDTO {
    private Long id;
    private String name;
    private String kind;
    private String command;
    private String stack;
    private String runtimeVersion;
    private String toolVersion;
    private Integer sortOrder;
    private java.util.Map<String, JobParamBindingDTO> paramBindings;
}
