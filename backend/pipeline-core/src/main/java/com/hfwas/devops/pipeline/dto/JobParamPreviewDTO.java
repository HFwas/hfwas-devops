package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.Map;

@Data
public class JobParamPreviewDTO {
    private String apiUrl;
    private String apiMethod = "GET";
    private Map<String, String> apiHeaders;
    private String apiResponsePath;
}