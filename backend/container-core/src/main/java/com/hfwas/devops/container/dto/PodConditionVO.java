package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PodConditionVO {
    private String type;
    private String status;
    private String reason;
    private String message;
    private LocalDateTime lastTransitionTime;
}