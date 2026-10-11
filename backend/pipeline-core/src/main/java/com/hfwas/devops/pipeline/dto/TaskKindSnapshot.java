package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

/** 发布时冻结的工作副本。激活后写回任务定义与参数，不包含启停状态。 */
@Data
public class TaskKindSnapshot {
    private String label;
    private String taskGroup;
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
    private List<TaskKindParamSaveDTO> params;
}
