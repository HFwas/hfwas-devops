package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HelmChartRepositoryVO {
    private Long id;
    private String name;
    private String type;
    private String url;
    private Boolean insecure;
    private LocalDateTime createdAt;
}
