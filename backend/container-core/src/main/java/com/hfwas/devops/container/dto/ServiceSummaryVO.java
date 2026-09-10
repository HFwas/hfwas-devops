package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceSummaryVO {
    private String name;
    private String namespace;
    private String type;
    private String clusterIP;
    private String externalIP;
    private int portCount;
    private String age;
    private LocalDateTime creationTimestamp;
}