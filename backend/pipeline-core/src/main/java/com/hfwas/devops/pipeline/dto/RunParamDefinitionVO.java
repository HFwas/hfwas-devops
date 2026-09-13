package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

@Data
public class RunParamDefinitionVO {
    private String paramKey;
    private String paramLabel;
    private String paramType;
    private String defaultValue;
    private Boolean required;
    private List<String> options;
    private Boolean loading;
    private String placeholder;
}