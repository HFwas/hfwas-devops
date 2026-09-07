package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CredentialVO {
    private Long id;
    private String name;
    private String kind;
    private String username;
    private LocalDateTime updateTime;
}
