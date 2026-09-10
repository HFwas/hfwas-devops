package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SecretSummaryVO {
    private String name;
    private String namespace;
    private String type;
    private int dataCount;
    private String age;
    private LocalDateTime creationTimestamp;
}