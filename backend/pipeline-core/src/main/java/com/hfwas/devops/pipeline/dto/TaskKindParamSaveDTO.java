package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TaskKindParamSaveDTO {
    private Long id;
    private String paramKey;
    private String paramLabel;
    private String paramType;
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
