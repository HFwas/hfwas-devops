package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class CredentialSaveDTO {
    private Long id;
    private String name;
    private String kind;
    private String username;
    private String secret;
}
