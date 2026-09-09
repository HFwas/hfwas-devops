package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class ContainerInfo {
    private String name;
    private String state;                 // "running" / "terminated" / "waiting" / "unknown"
    private Integer exitCode;
    private boolean hasShell;
    private String recommendedMode;       // "exec" / "ephemeral" / "debug_pod" / "unavailable"
    private String unavailableReason;     // mode=unavailable 时的中文说明
}