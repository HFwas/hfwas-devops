package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class JobParamDefinitionVO {
    private Long id;
    private Long pipelineId;
    private Long jobId;
    private String paramKey;
    private String paramLabel;
    private String paramType;         // input | select | api_select
    private String valueMode;         // fixed | runtime
    private String defaultValue;
    private Boolean required;
    private Integer sortOrder;
    private List<String> options;     // select 类型
    private String apiUrl;            // api_select 类型
    private String apiMethod;
    private Map<String, String> apiHeaders;
    private String apiResponsePath;
    private String placeholder;       // input 类型
}