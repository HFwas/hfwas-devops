package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RegistryUpdateDTO {
    private String alias;
    private String url;
    private Boolean insecure;
    private String credentialUsername;
    private String credentialPassword;
    private Map<String, String> labels;
}