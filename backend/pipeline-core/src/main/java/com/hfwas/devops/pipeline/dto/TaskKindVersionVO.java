package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskKindVersionVO {
    private String version;
    private String note;
    private LocalDateTime createdAt;
    private Boolean isCurrent;
}
