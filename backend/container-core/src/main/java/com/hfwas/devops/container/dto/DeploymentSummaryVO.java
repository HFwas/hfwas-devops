package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeploymentSummaryVO {
    private String name;
    private String namespace;
    private int desiredReplicas;
    private int readyReplicas;
    private int availableReplicas;
    private String strategy;
    private String age;
    private LocalDateTime creationTimestamp;
}