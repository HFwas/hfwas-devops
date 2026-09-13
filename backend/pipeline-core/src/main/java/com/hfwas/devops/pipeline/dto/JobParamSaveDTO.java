package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class JobParamSaveDTO {
    private Long pipelineId;
    private Long jobId;
    private String paramKey;
    private String paramLabel;
    private String paramType;
    private String valueMode;         // fixed | runtime
    private String defaultValue;
    private Boolean required;
    private Integer sortOrder;
    private List<String> options;
    private String apiUrl;
    private String apiMethod;
    private Map<String, String> apiHeaders;
    private String apiResponsePath;
    private String placeholder;
}