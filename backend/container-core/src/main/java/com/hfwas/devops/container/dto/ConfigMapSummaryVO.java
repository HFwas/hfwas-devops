package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfigMapSummaryVO {
    private String name;
    private String namespace;
    private int dataCount;
    private String age;
    private LocalDateTime creationTimestamp;
}