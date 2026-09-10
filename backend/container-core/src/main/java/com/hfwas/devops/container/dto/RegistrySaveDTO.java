package com.hfwas.devops.container.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class RegistrySaveDTO {
    @NotBlank
    private String name;
    private String alias;
    @NotBlank
    private String type = "harbor";  // harbor / registry_v2
    @NotBlank
    private String url;
    private Boolean insecure;
    private String credentialUsername;
    private String credentialPassword;
    private Long clusterId;
    private Map<String, String> labels;
}