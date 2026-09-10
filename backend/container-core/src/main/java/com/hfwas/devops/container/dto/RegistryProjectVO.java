package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class RegistryProjectVO {
    private String name;
    private Long repoCount;
    private String creationTime;
    private String updateTime;
}