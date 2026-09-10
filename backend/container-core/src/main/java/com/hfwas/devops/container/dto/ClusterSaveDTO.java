package com.hfwas.devops.container.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ClusterSaveDTO {
    @NotBlank
    private String name;
    private String alias;
    private String provider;
    @NotBlank
    private String kubeconfig;
    private String mode = "proxy";
    private Map<String, String> labels;
}