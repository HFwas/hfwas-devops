package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PipelineRunStartDTO {
    private Map<String, String> params;
}