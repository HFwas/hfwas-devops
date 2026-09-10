package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class ScanOverviewVO {
    private String status;       // "pending" / "running" / "completed" / "error"
    private String severity;     // "Critical" / "High" / "Medium" / "Low" / "None"
    private Integer totalVulnerabilities;
    private Integer critical;
    private Integer high;
    private Integer medium;
    private Integer low;
}