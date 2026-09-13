package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

@Data
public class JobParamPreviewResultVO {
    private boolean success;
    private List<String> options;
    private String errorMessage;
}