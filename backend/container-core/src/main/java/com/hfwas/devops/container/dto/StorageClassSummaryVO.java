package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StorageClassSummaryVO {
    private String name;
    private String provisioner;
    private String reclaimPolicy;
    private String volumeBindingMode;
    private Boolean allowVolumeExpansion;
    private String age;
    private LocalDateTime creationTimestamp;
}