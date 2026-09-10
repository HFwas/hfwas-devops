package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StatefulSetSummaryVO {
    private String name;
    private String namespace;
    private int desiredReplicas;
    private int readyReplicas;
    private int currentReplicas;
    private String serviceName;
    private String age;
    private LocalDateTime creationTimestamp;
}