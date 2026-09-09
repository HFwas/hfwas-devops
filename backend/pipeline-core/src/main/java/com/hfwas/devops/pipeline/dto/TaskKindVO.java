package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class TaskKindVO {
    private String kindValue;
    private String label;
    private String taskGroup;
    private String description;
    private String hint;
    private String defaultCommand;
    private Boolean requiresCommand;
    private Boolean enabled;
    private Integer sortOrder;
    private String toolImage;
    private String defaultImage;
    private String commandTemplate;
}