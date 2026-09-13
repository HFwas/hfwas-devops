package com.hfwas.devops.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskKindUpdateDTO {
    @NotBlank
    private String label;
    private String description;
    private String hint;
    private String defaultCommand;
    private String toolImage;
    private String commandTemplate;
    private Integer sortOrder;
    private String cpuRequest;
    private String cpuLimit;
    private String memoryRequest;
    private String memoryLimit;
    private java.util.List<TaskKindParamSaveDTO> params;
}