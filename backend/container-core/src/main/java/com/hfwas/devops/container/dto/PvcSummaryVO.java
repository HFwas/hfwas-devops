package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PvcSummaryVO {
    private String name;
    private String namespace;
    private String status;
    private String accessModes;
    private String storageClass;
    private String capacity;
    private String age;
    private LocalDateTime creationTimestamp;
}