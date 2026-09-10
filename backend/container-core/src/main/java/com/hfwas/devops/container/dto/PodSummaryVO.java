package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PodSummaryVO {
    private String name;
    private String namespace;
    private String status;
    private String nodeName;
    private String podIP;
    private int containerCount;
    private int readyContainers;
    private int restarts;
    private String age;
    private LocalDateTime creationTimestamp;
}