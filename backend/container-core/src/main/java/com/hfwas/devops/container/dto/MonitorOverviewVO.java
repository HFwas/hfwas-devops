package com.hfwas.devops.container.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorOverviewVO {
    private double cpuUsagePercent;
    private double memoryUsagePercent;
    private int nodeTotal;
    private int nodeReady;
    private int podTotal;
    private int podRunning;
    private long diskReadBytesPerSec;
    private long diskWriteBytesPerSec;
}