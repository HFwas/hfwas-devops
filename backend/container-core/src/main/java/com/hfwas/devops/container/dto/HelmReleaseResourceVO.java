package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class HelmReleaseResourceVO {
    private String apiVersion;
    private String kind;
    private String namespace;
    private String name;
    private String status;
}
