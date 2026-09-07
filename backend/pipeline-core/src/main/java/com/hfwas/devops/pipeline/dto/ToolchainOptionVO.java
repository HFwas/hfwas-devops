package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class ToolchainOptionVO {
    private String stack;
    private String runtimeVersion;
    private String toolVersion;
    private String image;
    private String buildCommand;
    private String testCommand;
}
