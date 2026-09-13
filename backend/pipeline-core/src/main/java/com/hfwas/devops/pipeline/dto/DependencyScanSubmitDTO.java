package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class DependencyScanSubmitDTO {
    private String repoUrl;
    private String gitRef;
    private Long credentialId;
    private String stack;          // JAVA_MAVEN / NODE / GO / PYTHON，为空则自动检测
    private String runtimeVersion;
    private String modulePath;     // 模块路径，如 backend/ frontend/，为空则扫仓库根目录
}