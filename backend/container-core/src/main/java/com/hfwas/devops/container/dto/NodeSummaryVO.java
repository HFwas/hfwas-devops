package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class NodeSummaryVO {
    private String name;
    private String status;
    private String role;
    private String kubeletVersion;
    private String containerRuntime;
    private String osImage;
    private String kernelVersion;
    private String architecture;
    private String podCIDR;
    private String providerID;
    private int podCount;
    private long memoryCapacity;
    private int cpuCapacity;
    private String age;
    private LocalDateTime creationTimestamp;
}