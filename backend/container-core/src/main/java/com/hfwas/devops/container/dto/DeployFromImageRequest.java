package com.hfwas.devops.container.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DeployFromImageRequest {
    @NotNull
    private Long clusterId;

    @NotBlank
    private String namespace;

    @NotBlank
    private String name;

    @NotBlank
    private String image;

    private Integer replicas = 1;

    private Integer containerPort;

    private Boolean createPullSecret = true;

    private String pullSecretName;

    private List<EnvVar> env;

    private ResourceSpec resources;

    @Data
    public static class EnvVar {
        private String name;
        private String value;
    }

    @Data
    public static class ResourceSpec {
        private String cpu;
        private String memory;
    }
}