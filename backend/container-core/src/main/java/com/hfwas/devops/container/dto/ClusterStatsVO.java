package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class ClusterStatsVO {
    private int nodeCount;
    private int podCount;
    private int deploymentCount;
    private int serviceCount;
    private int namespaceCount;
    private double cpuTotal;
    private long memoryTotal;
}