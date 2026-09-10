package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NamespaceVO {
    private String name;
    private String status;   // Active / Terminating
    private LocalDateTime creationTimestamp;
    private String phase;
    private String uid;
}