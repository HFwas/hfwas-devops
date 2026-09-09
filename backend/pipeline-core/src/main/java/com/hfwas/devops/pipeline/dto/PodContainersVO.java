package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.util.List;

@Data
public class PodContainersVO {
    private String namespace;
    private String podName;
    private String podExists;             // "true" / "false" / "unknown"
    private String workspaceKind;         // "pvc" / "emptydir" / "unknown"
    private String workspacePath;         // "/workspace/source/src"
    private List<ContainerInfo> containers;
    private String defaultContainer;
}